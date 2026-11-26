import { describe, expect, it, vi } from 'vitest';
import { AuthApplicationService } from '../../src/application/services/AuthApplicationService';
import { AuthError } from '../../src/domain/exceptions/AuthError';
import type { AuthSession, RemoteSignOut, SessionState } from '../../src/domain/model/AuthSession';
import type { AuthGateway, SessionChangeListener } from '../../src/domain/ports/out/AuthGateway';

const session = (id: string, expiresAt = 1000): AuthSession => ({ user: { id, email: `${id}@test.co` }, expiresAt });

// Pasarela falsa cuyo "almacenamiento" es la propiedad stored
class FakeGateway implements AuthGateway {
  stored: AuthSession | null = null;
  listener: SessionChangeListener | null = null;
  currentSession = vi.fn(async (): Promise<AuthSession | null> => this.stored);
  signInWithPassword = vi.fn(async (email: string): Promise<AuthSession> => {
    this.stored = session(email.split('@')[0]);
    return this.stored;
  });
  signOut = vi.fn(async (): Promise<RemoteSignOut> => {
    this.stored = null;
    return 'confirmed';
  });
  onSessionChange(listener: SessionChangeListener) {
    this.listener = listener;
    return () => {
      this.listener = null;
    };
  }
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('AuthApplicationService', () => {
  it('arranca en initializing y recupera la sesión guardada', async () => {
    const gateway = new FakeGateway();
    gateway.stored = session('a');
    const service = new AuthApplicationService(gateway);
    expect(service.state.status).toBe('initializing');
    expect(await service.initialize()).toEqual({ status: 'signedIn', session: session('a') });
  });

  it('sin red queda no disponible y permite reintentar', async () => {
    const gateway = new FakeGateway();
    gateway.currentSession.mockRejectedValueOnce(new AuthError('NETWORK'));
    const service = new AuthApplicationService(gateway);
    await expect(service.initialize()).rejects.toMatchObject({ code: 'NETWORK' });
    expect(service.state).toEqual({ status: 'unavailable', reason: 'NETWORK' });
    gateway.stored = session('a');
    await expect(service.initialize()).resolves.toMatchObject({ status: 'signedIn' });
  });

  it('sin configuración queda no disponible con MISCONFIGURED', async () => {
    const gateway = new FakeGateway();
    gateway.currentSession.mockRejectedValue(new AuthError('MISCONFIGURED'));
    const service = new AuthApplicationService(gateway);
    await expect(service.initialize()).rejects.toMatchObject({ code: 'MISCONFIGURED' });
    expect(service.state).toEqual({ status: 'unavailable', reason: 'MISCONFIGURED' });
  });

  it('inicia y cierra sesión notificando a los suscriptores', async () => {
    const service = new AuthApplicationService(new FakeGateway());
    const seen: SessionState['status'][] = [];
    service.subscribe((state) => seen.push(state.status));
    await service.initialize();
    await service.signIn('a@test.co', 'secreta');
    await service.signOut();
    expect(seen).toEqual(['initializing', 'signedOut', 'signedIn', 'signedOut']);
  });

  it('ejecuta las operaciones de una en una', async () => {
    const gateway = new FakeGateway();
    let active = 0;
    let maxActive = 0;
    gateway.signInWithPassword.mockImplementation(async (email: string) => {
      maxActive = Math.max(maxActive, ++active);
      await settle();
      active -= 1;
      gateway.stored = session(email.split('@')[0]);
      return gateway.stored;
    });
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    await Promise.allSettled([service.signIn('a@test.co', 'x'), service.signOut(), service.signIn('b@test.co', 'x')]);
    expect(maxActive).toBe(1);
    expect(service.state).toEqual({ status: 'signedIn', session: session('b') });
  });

  it('rechaza entrar con otra cuenta sin cerrar la actual', async () => {
    const gateway = new FakeGateway();
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    await service.signIn('a@test.co', 'x');
    await expect(service.signIn('b@test.co', 'x')).rejects.toMatchObject({ code: 'CONFLICT' });
    expect(gateway.signInWithPassword).toHaveBeenCalledTimes(1);
  });

  it('propaga credenciales inválidas sin cambiar el estado', async () => {
    const gateway = new FakeGateway();
    gateway.signInWithPassword.mockRejectedValue(new AuthError('INVALID_CREDENTIALS'));
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    await expect(service.signIn('a@test.co', 'mala')).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(service.state.status).toBe('signedOut');
  });

  it('no confirma el login si lo guardado no coincide', async () => {
    const gateway = new FakeGateway();
    gateway.signInWithPassword.mockResolvedValue(session('a'));
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    await expect(service.signIn('a@test.co', 'x')).rejects.toMatchObject({ code: 'STORAGE' });
    expect(service.state).toEqual({ status: 'unavailable', reason: 'STORAGE' });
  });

  it('no confirma el logout si la sesión sigue guardada', async () => {
    const gateway = new FakeGateway();
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    await service.signIn('a@test.co', 'x');
    // La pasarela dice que cerró, pero la sesión sigue guardada
    gateway.signOut.mockResolvedValueOnce('confirmed');
    await expect(service.signOut()).rejects.toMatchObject({ code: 'STORAGE' });
    expect(service.state).toEqual({ status: 'unavailable', reason: 'STORAGE' });
  });

  it('tras un logout fallido, inicializar reintenta la invalidación en vez de restaurar', async () => {
    const gateway = new FakeGateway();
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    await service.signIn('a@test.co', 'x');
    gateway.signOut.mockRejectedValueOnce(new AuthError('STORAGE'));
    await expect(service.signOut()).rejects.toMatchObject({ code: 'STORAGE' });

    gateway.signOut.mockRejectedValueOnce(new AuthError('STORAGE'));
    await service.initialize();
    expect(service.state).toEqual({ status: 'unavailable', reason: 'STORAGE' });

    await service.initialize();
    expect(service.state.status).toBe('signedOut');
  });

  it('los avisos del proveedor se resuelven leyendo lo guardado', async () => {
    const gateway = new FakeGateway();
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    gateway.stored = session('a', 2000);
    gateway.listener?.();
    await settle();
    expect(service.state).toEqual({ status: 'signedIn', session: session('a', 2000) });
    gateway.stored = null;
    gateway.listener?.();
    await settle();
    expect(service.state.status).toBe('signedOut');
  });

  it('no repite notificaciones si el estado no cambia y permite desuscribirse', async () => {
    const gateway = new FakeGateway();
    const service = new AuthApplicationService(gateway);
    const listener = vi.fn();
    const unsubscribe = service.subscribe(listener);
    await service.initialize();
    gateway.listener?.();
    await settle();
    expect(listener).toHaveBeenCalledTimes(2);
    unsubscribe();
    gateway.stored = session('a');
    gateway.listener?.();
    await settle();
    expect(listener).toHaveBeenCalledTimes(2);
  });
});

describe('AuthApplicationService: resultado del cierre', () => {
  it('devuelve "sesión local cerrada; revocación remota no confirmada" sin bloquear', async () => {
    const gateway = new FakeGateway();
    const service = new AuthApplicationService(gateway);
    await service.initialize();
    await service.signIn('a@test.co', 'x');
    gateway.signOut.mockImplementationOnce(async () => {
      gateway.stored = null;
      return 'unconfirmed';
    });
    await expect(service.signOut()).resolves.toEqual({ localSession: 'closedAndVerified', remoteSignOut: 'unconfirmed' });
    expect(service.state.status).toBe('signedOut');
  });
});
