import { AuthApiError, AuthRetryableFetchError, AuthSessionMissingError } from '@supabase/supabase-js';
import type { Session } from '@supabase/supabase-js';
import { describe, expect, it, vi } from 'vitest';
import { SupabaseAuthGateway, type SupabaseAuthApi } from '../../src/adapters/supabase/SupabaseAuthGateway';
import { toAuthError } from '../../src/adapters/supabase/supabase-errors';
import { AuthError } from '../../src/domain/exceptions/AuthError';

const sdkSession = {
  access_token: 'jwt-secreto',
  refresh_token: 'refresh-secreto',
  expires_at: 1_700_000_000,
  user: { id: 'u1', email: 'u1@test.co' },
} as unknown as Session;

function fakeAuth(overrides: Partial<Record<keyof SupabaseAuthApi, unknown>> = {}) {
  let callback: ((event: string, session: Session | null) => void) | null = null;
  const unsubscribe = vi.fn();
  const auth = {
    getSession: vi.fn(async () => ({ data: { session: sdkSession }, error: null })),
    signInWithPassword: vi.fn(async () => ({ data: { session: sdkSession, user: sdkSession.user }, error: null })),
    signOut: vi.fn(async () => ({ error: null })),
    onAuthStateChange: vi.fn((cb: typeof callback) => {
      callback = cb;
      return { data: { subscription: { unsubscribe } } };
    }),
    ...overrides,
  };
  return { auth: auth as unknown as SupabaseAuthApi, raw: auth, emit: (e: string, s: Session | null) => callback?.(e, s), unsubscribe };
}

const apiError = (status: number, code?: string, message = 'error') => new AuthApiError(message, status, code);

describe('SupabaseAuthGateway', () => {
  it('expone solo usuario y vencimiento, nunca los tokens', async () => {
    const { auth } = fakeAuth();
    const session = await new SupabaseAuthGateway(auth).currentSession();
    expect(session).toEqual({ user: { id: 'u1', email: 'u1@test.co' }, expiresAt: 1_700_000_000_000 });
    expect(JSON.stringify(session)).not.toContain('secreto');
  });

  it('trata un refresh token rechazado como sesión inexistente', async () => {
    const { auth } = fakeAuth({
      getSession: vi.fn(async () => ({ data: { session: null }, error: apiError(400, 'refresh_token_not_found') })),
    });
    await expect(new SupabaseAuthGateway(auth).currentSession()).resolves.toBeNull();
  });

  it('propaga un fallo de red al recuperar la sesión', async () => {
    const { auth } = fakeAuth({
      getSession: vi.fn(async () => ({ data: { session: null }, error: new AuthRetryableFetchError('offline', 0) })),
    });
    await expect(new SupabaseAuthGateway(auth).currentSession()).rejects.toMatchObject({ code: 'NETWORK' });
  });

  it('propaga errores del almacenamiento seguro sin envolverlos', async () => {
    const { auth } = fakeAuth({ getSession: vi.fn(async () => Promise.reject(new AuthError('STORAGE'))) });
    await expect(new SupabaseAuthGateway(auth).currentSession()).rejects.toMatchObject({ code: 'STORAGE' });
  });

  it('inicia sesión con correo y contraseña', async () => {
    const { auth, raw } = fakeAuth();
    const session = await new SupabaseAuthGateway(auth).signInWithPassword('u1@test.co', 'clave');
    expect(raw.signInWithPassword).toHaveBeenCalledWith({ email: 'u1@test.co', password: 'clave' });
    expect(session.user.id).toBe('u1');
  });

  it('error null del SDK no se toma como revocación confirmada', async () => {
    const { auth, raw } = fakeAuth();
    await expect(new SupabaseAuthGateway(auth).signOut()).resolves.toBe('unconfirmed');
    expect(raw.signOut).toHaveBeenCalledWith({ scope: 'local' });
    // Una sola petición de cierre: no se repite la revocación para averiguar el resultado
    expect(raw.signOut).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['sin red', new AuthRetryableFetchError('offline', 0)],
    ['HTTP 429', apiError(429, 'over_request_rate_limit')],
    ['HTTP 500', apiError(500, 'unexpected_failure')],
  ])('un error remoto (%s) es "no confirmado", no STORAGE', async (_name, error) => {
    const { auth } = fakeAuth({ signOut: vi.fn(async () => ({ error })) });
    await expect(new SupabaseAuthGateway(auth).signOut()).resolves.toBe('unconfirmed');
  });

  it('sin sesión previa no hay revocación que pedir', async () => {
    const { auth } = fakeAuth({ getSession: vi.fn(async () => ({ data: { session: null }, error: null })) });
    await expect(new SupabaseAuthGateway(auth).signOut()).resolves.toBe('notRequired');
  });

  it('los eventos solo avisan: no entregan la sesión e ignoran la inicial', () => {
    const { auth, emit, unsubscribe } = fakeAuth();
    const listener = vi.fn();
    const stop = new SupabaseAuthGateway(auth).onSessionChange(listener);
    emit('INITIAL_SESSION', sdkSession);
    emit('TOKEN_REFRESHED', sdkSession);
    emit('SIGNED_OUT', null);
    expect(listener.mock.calls).toEqual([[], []]);
    stop();
    expect(unsubscribe).toHaveBeenCalled();
  });

  it('propaga STORAGE cuando el SDK no pudo borrar la sesión', async () => {
    const { auth } = fakeAuth({ signOut: vi.fn(async () => Promise.reject(new AuthError('STORAGE'))) });
    await expect(new SupabaseAuthGateway(auth).signOut()).rejects.toMatchObject({ code: 'STORAGE' });
  });
});

describe('toAuthError', () => {
  it.each([
    [apiError(400, 'invalid_credentials'), 'INVALID_CREDENTIALS'],
    [apiError(400, undefined, 'Invalid login credentials'), 'INVALID_CREDENTIALS'],
    [apiError(400, 'email_not_confirmed'), 'EMAIL_NOT_CONFIRMED'],
    [apiError(429, 'over_request_rate_limit'), 'RATE_LIMITED'],
    [apiError(429), 'RATE_LIMITED'],
    [apiError(403, 'session_expired'), 'SESSION_EXPIRED'],
    [new AuthSessionMissingError(), 'SESSION_EXPIRED'],
    [Object.assign(new AuthSessionMissingError(), { name: 'AuthRefreshDiscardedError' }), 'SESSION_EXPIRED'],
    [new AuthRetryableFetchError('offline', 0), 'NETWORK'],
    [apiError(500, 'unexpected_failure'), 'UNKNOWN'],
    [new TypeError('x'), 'UNKNOWN'],
  ])('%s → %s', (error, code) => {
    expect(toAuthError(error).code).toBe(code);
  });

  it('no copia el mensaje original del servidor', () => {
    const mapped = toAuthError(apiError(400, 'invalid_credentials', 'detalle con usuario@correo.co'));
    expect(mapped.message).not.toContain('usuario@correo.co');
  });
});
