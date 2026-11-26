import { afterEach, describe, expect, it } from 'vitest';
import type { SupabaseClient } from '@supabase/supabase-js';
import { MemorySessionStore } from '../../src/adapters/storage/MemorySessionStore';
import { authStorageKey, createSupabaseClient } from '../../src/adapters/supabase/createSupabaseClient';
import { SupabaseAuthGateway } from '../../src/adapters/supabase/SupabaseAuthGateway';
import { AuthApplicationService } from '../../src/application/services/AuthApplicationService';
import { AuthError } from '../../src/domain/exceptions/AuthError';
import { createFakeAuthServer, flush } from '../support/fake-supabase';

// SDK real de Supabase contra un servidor de Auth simulado
const CONFIG = { url: 'https://proyectoprueba.supabase.co', publishableKey: 'sb_publishable_prueba123', projectRef: 'proyectoprueba' };
const KEY = authStorageKey(CONFIG.projectRef);

// Almacén en memoria que puede fallar como un disco real
class TestStore extends MemorySessionStore {
  failSet = false;
  failRemove = false;

  override async setItem(key: string, value: string): Promise<void> {
    if (this.failSet) throw new AuthError('STORAGE');
    return super.setItem(key, value);
  }

  override async removeItem(key: string): Promise<void> {
    if (this.failRemove) throw new AuthError('STORAGE');
    return super.removeItem(key);
  }
}

const clients: SupabaseClient[] = [];

function setup(store = new TestStore()) {
  const server = createFakeAuthServer();
  const client = createSupabaseClient(CONFIG, store, { storageKey: KEY, fetch: server.fetch });
  clients.push(client);
  const service = new AuthApplicationService(new SupabaseAuthGateway(client.auth));
  return { server, client, service, store };
}

// Usuario según el estado visible, el SDK y el almacenamiento: deben coincidir
async function snapshot({ service, client, store }: ReturnType<typeof setup>) {
  const visible = service.state.status === 'signedIn' ? service.state.session.user.id : service.state.status;
  const { data } = await client.auth.getSession();
  const raw = await store.getItem(KEY);
  return {
    visible,
    sdk: data.session?.user.id ?? null,
    stored: raw ? (JSON.parse(raw) as { user: { id: string } }).user.id : null,
  };
}

// Simula reabrir la app: cliente y servicio nuevos sobre el mismo almacén
async function reopen(store: TestStore) {
  const ctx = setup(store);
  await ctx.service.initialize().catch(() => undefined);
  return ctx;
}

const passwordCalls = (calls: string[]) => calls.filter((route) => route === 'password').length;

afterEach(async () => {
  await Promise.all(clients.splice(0).map((client) => client.auth.dispose()));
});

describe('SDK real: el problema que motiva la cola', () => {
  it('sin coordinación, la respuesta más tardía sobrescribe la sesión guardada', async () => {
    const { client, server, store } = setup();
    const releaseA = server.hold('password');
    const a = client.auth.signInWithPassword({ email: 'a@prueba.test', password: 'x' });
    await client.auth.signInWithPassword({ email: 'b@prueba.test', password: 'x' });
    releaseA();
    await a;
    // Quien pidió B al final termina con A guardada
    expect(JSON.parse((await store.getItem(KEY))!).user.id).toBe('a');
  });
});

describe('AuthApplicationService con el SDK real', () => {
  it('login A/B concurrentes: no se envía B y todo queda en A', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    const releaseA = ctx.server.hold('password');
    const a = ctx.service.signIn('a@prueba.test', 'x');
    const b = ctx.service.signIn('b@prueba.test', 'x');
    await flush();
    expect(passwordCalls(ctx.server.calls)).toBe(1);

    releaseA();
    await expect(a).resolves.toMatchObject({ status: 'signedIn' });
    await expect(b).rejects.toMatchObject({ code: 'CONFLICT' });
    expect(passwordCalls(ctx.server.calls)).toBe(1);
    expect(await snapshot(ctx)).toEqual({ visible: 'a', sdk: 'a', stored: 'a' });
    expect(await snapshot(await reopen(ctx.store))).toEqual({ visible: 'a', sdk: 'a', stored: 'a' });
  });

  it('logout durante login: el cierre espera al login y nada queda guardado', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    const release = ctx.server.hold('password');
    const login = ctx.service.signIn('a@prueba.test', 'x');
    const logout = ctx.service.signOut();
    await flush();
    release();
    await login;
    await logout;
    await flush();
    expect(await snapshot(ctx)).toEqual({ visible: 'signedOut', sdk: null, stored: null });
    expect(await snapshot(await reopen(ctx.store))).toEqual({ visible: 'signedOut', sdk: null, stored: null });
  });

  it('refresh durante logout: el SDK descarta los tokens rotados y no reaparece la sesión', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    await ctx.service.signIn('a@prueba.test', 'x');

    const releaseRefresh = ctx.server.hold('refresh');
    const refresh = ctx.client.auth.refreshSession();
    await flush();
    await ctx.service.signOut();
    releaseRefresh();
    const result = await refresh;
    await flush();

    expect(result.error?.name).toBe('AuthRefreshDiscardedError');
    expect(await snapshot(ctx)).toEqual({ visible: 'signedOut', sdk: null, stored: null });
    expect(await snapshot(await reopen(ctx.store))).toEqual({ visible: 'signedOut', sdk: null, stored: null });
  });

  it('una renovación normal actualiza el estado desde lo guardado', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    await ctx.service.signIn('a@prueba.test', 'x');
    const before = await ctx.store.getItem(KEY);
    await ctx.client.auth.refreshSession();
    await flush();
    expect(await ctx.store.getItem(KEY)).not.toBe(before);
    expect(await snapshot(ctx)).toEqual({ visible: 'a', sdk: 'a', stored: 'a' });
  });

  it('cambio de cuenta: exige cerrar la sesión antes de entrar con otra', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    await ctx.service.signIn('a@prueba.test', 'x');
    await expect(ctx.service.signIn('b@prueba.test', 'x')).rejects.toMatchObject({ code: 'CONFLICT' });
    expect(await snapshot(ctx)).toEqual({ visible: 'a', sdk: 'a', stored: 'a' });

    await ctx.service.signOut();
    await ctx.service.signIn('b@prueba.test', 'x');
    expect(await snapshot(ctx)).toEqual({ visible: 'b', sdk: 'b', stored: 'b' });
  });

  it('fallo de borrado en logout: no informa éxito ni restaura la sesión antigua', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    await ctx.service.signIn('a@prueba.test', 'x');

    ctx.store.failRemove = true;
    await expect(ctx.service.signOut()).rejects.toMatchObject({ code: 'STORAGE' });
    expect(ctx.service.state).toEqual({ status: 'unavailable', reason: 'STORAGE' });

    // Aunque la sesión sigue en el almacén, reintentar la inicialización no la restaura
    await ctx.service.initialize();
    expect(ctx.service.state).toEqual({ status: 'unavailable', reason: 'STORAGE' });
    await expect(ctx.service.signIn('b@prueba.test', 'x')).rejects.toMatchObject({ code: 'STORAGE' });

    // Cuando el almacenamiento responde, la invalidación pendiente se completa
    ctx.store.failRemove = false;
    await ctx.service.initialize();
    expect(await snapshot(ctx)).toEqual({ visible: 'signedOut', sdk: null, stored: null });
  });

  it('fallo de escritura en login: bloquea el acceso y limpia al recuperarse', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    ctx.store.failSet = true;
    await expect(ctx.service.signIn('a@prueba.test', 'x')).rejects.toMatchObject({ code: 'STORAGE' });
    expect(ctx.service.state).toEqual({ status: 'unavailable', reason: 'STORAGE' });

    ctx.store.failSet = false;
    await ctx.service.initialize();
    expect(await snapshot(ctx)).toEqual({ visible: 'signedOut', sdk: null, stored: null });
  });

  it('restaura una sesión válida al reabrir', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    await ctx.service.signIn('a@prueba.test', 'x');
    expect(await snapshot(await reopen(ctx.store))).toEqual({ visible: 'a', sdk: 'a', stored: 'a' });
  });
});

describe('logout: borrado local frente a revocación remota (SDK real)', () => {
  async function signedIn() {
    const ctx = setup();
    await ctx.service.initialize();
    await ctx.service.signIn('a@prueba.test', 'x');
    return ctx;
  }

  it.each([401, 403, 404])('el SDK devuelve error null ante HTTP %i en /logout (motivo de no confiar en él)', async (status) => {
    const ctx = await signedIn();
    ctx.server.failLogout(status);
    const { error } = await ctx.client.auth.signOut({ scope: 'local' });
    expect(error).toBeNull();
  });

  it('aun con HTTP 204 el SDK no expone evidencia: revocación no confirmada', async () => {
    const ctx = await signedIn();
    await expect(ctx.service.signOut()).resolves.toEqual({ localSession: 'closedAndVerified', remoteSignOut: 'unconfirmed' });
    expect(ctx.server.calls).toContain('logout');
    expect(await snapshot(ctx)).toEqual({ visible: 'signedOut', sdk: null, stored: null });
  });

  it.each([
    ['HTTP 401 (aceptado por el SDK)', 401],
    ['HTTP 403 (aceptado por el SDK)', 403],
    ['HTTP 404 (aceptado por el SDK)', 404],
    ['HTTP 429', 429],
    ['HTTP 500', 500],
    ['sin red', 'network'],
  ] as const)('%s: sesión local cerrada, revocación remota no confirmada (no es STORAGE)', async (_name, failure) => {
    const ctx = await signedIn();
    ctx.server.failLogout(failure);

    await expect(ctx.service.signOut()).resolves.toEqual({ localSession: 'closedAndVerified', remoteSignOut: 'unconfirmed' });
    expect(ctx.service.state).toEqual({ status: 'signedOut' });
    // Se verifica el SDK y el almacenamiento, también al reabrir
    expect(await snapshot(ctx)).toEqual({ visible: 'signedOut', sdk: null, stored: null });
    expect(await snapshot(await reopen(ctx.store))).toEqual({ visible: 'signedOut', sdk: null, stored: null });
  });

  it('sin sesión no hay nada que revocar', async () => {
    const ctx = setup();
    await ctx.service.initialize();
    await expect(ctx.service.signOut()).resolves.toEqual({ localSession: 'closedAndVerified', remoteSignOut: 'notRequired' });
    expect(ctx.server.calls).not.toContain('logout');
  });

  it('si además falla el borrado local, prevalece STORAGE y se mantiene el bloqueo', async () => {
    const ctx = await signedIn();
    ctx.server.failLogout(429);
    ctx.store.failRemove = true;

    await expect(ctx.service.signOut()).rejects.toMatchObject({ code: 'STORAGE' });
    expect(ctx.service.state).toEqual({ status: 'unavailable', reason: 'STORAGE' });
    expect((await snapshot(ctx)).stored).toBe('a');

    // Al recuperarse el almacenamiento, la invalidación pendiente se completa
    ctx.store.failRemove = false;
    await ctx.service.initialize();
    expect(await snapshot(ctx)).toEqual({ visible: 'signedOut', sdk: null, stored: null });
  });
});
