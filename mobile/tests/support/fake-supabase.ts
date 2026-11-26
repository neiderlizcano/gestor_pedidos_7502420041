// Servidor de Auth simulado para usar el SDK real sin red ni credenciales reales

export interface Deferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

export function deferred<T = void>(): Deferred<T> {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

const base64Url = (value: object) =>
  btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');

let counter = 0;

// Sesión con el formato de GoTrue; los tokens son ficticios
export function fakeSession(userId: string, expiresInSeconds = 3600) {
  counter += 1;
  const now = Math.floor(Date.now() / 1000);
  const exp = now + expiresInSeconds;
  return {
    access_token: `${base64Url({ alg: 'HS256' })}.${base64Url({ sub: userId, exp, role: 'authenticated' })}.firma`,
    token_type: 'bearer',
    expires_in: expiresInSeconds,
    expires_at: exp,
    refresh_token: `refresh-${userId}-${counter}`,
    user: {
      id: userId,
      email: `${userId}@prueba.test`,
      aud: 'authenticated',
      role: 'authenticated',
      app_metadata: {},
      user_metadata: {},
      created_at: '2026-01-01T00:00:00Z',
    },
  };
}

export type Route = 'password' | 'refresh' | 'logout';

/**
 * fetch simulado. Por defecto responde al instante; `hold(route)` retiene la
 * siguiente petición de esa ruta hasta llamar a release().
 */
export function createFakeAuthServer() {
  const held = new Map<Route, Deferred<void>[]>();
  const calls: Route[] = [];
  // Respuesta de /logout: código HTTP de error o fallo de red
  let logoutFailure: number | 'network' | null = null;

  const routeOf = (url: string): Route | null => {
    if (url.includes('grant_type=password')) return 'password';
    if (url.includes('grant_type=refresh_token')) return 'refresh';
    if (url.includes('/logout')) return 'logout';
    return null;
  };

  const fetchImpl: typeof fetch = async (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const route = routeOf(url);
    if (!route) return new Response('{}', { status: 404 });
    calls.push(route);
    const gate = held.get(route)?.shift();
    if (gate) await gate.promise;

    if (route === 'logout') {
      if (logoutFailure === 'network') throw new TypeError('Failed to fetch');
      if (typeof logoutFailure === 'number') {
        const codes: Record<number, string> = {
          401: 'bad_jwt',
          403: 'session_not_found',
          404: 'user_not_found',
          429: 'over_request_rate_limit',
        };
        const body = { code: logoutFailure, error_code: codes[logoutFailure] ?? 'unexpected_failure', msg: 'fallo simulado' };
        return new Response(JSON.stringify(body), { status: logoutFailure, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(null, { status: 204 });
    }
    const body = JSON.parse(String(init?.body ?? '{}')) as { email?: string; refresh_token?: string };
    const userId = route === 'password' ? String(body.email).split('@')[0] : String(body.refresh_token).split('-')[1];
    return new Response(JSON.stringify(fakeSession(userId)), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  };

  return {
    fetch: fetchImpl,
    calls,
    failLogout(failure: number | 'network' | null) {
      logoutFailure = failure;
    },
    // Retiene la siguiente petición de la ruta; devuelve la función para soltarla
    hold(route: Route): () => void {
      const gate = deferred<void>();
      held.set(route, [...(held.get(route) ?? []), gate]);
      return () => gate.resolve();
    },
  };
}

// Espera a que se procesen las promesas pendientes
export const flush = () => new Promise((resolve) => setTimeout(resolve, 20));
