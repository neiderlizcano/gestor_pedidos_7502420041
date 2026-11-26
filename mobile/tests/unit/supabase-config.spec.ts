import { describe, expect, it } from 'vitest';
import { assertPublicEnv, readLegacyJwtPayload, resolveSupabaseConfig } from '../../src/bootstrap/supabase-config';
import { ConfigurationError } from '../../src/domain/exceptions/errors';

// JWT con firma ficticia: solo sirve para probar la lectura del tipo de clave declarado
const b64 = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
const legacyKey = (payload: object) => [b64({ alg: 'HS256', typ: 'JWT' }), b64(payload), 'firma'].join('.');

const URL_OK = 'https://proyectoprueba.supabase.co';
const KEY_OK = 'sb_publishable_prueba123';

describe('resolveSupabaseConfig', () => {
  it('acepta URL HTTPS de proyecto y clave publicable, y obtiene el identificador', () => {
    expect(resolveSupabaseConfig({ VITE_SUPABASE_URL: `${URL_OK}/`, VITE_SUPABASE_PUBLISHABLE_KEY: ` ${KEY_OK} ` })).toEqual({
      url: URL_OK,
      publishableKey: KEY_OK,
      projectRef: 'proyectoprueba',
    });
  });

  it('acepta una clave anon heredada del mismo proyecto', () => {
    const key = legacyKey({ role: 'anon', ref: 'proyectoprueba' });
    expect(resolveSupabaseConfig({ VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: key }).publishableKey).toBe(key);
  });

  it('usa el host completo como identificador en dominios propios', () => {
    expect(resolveSupabaseConfig({ VITE_SUPABASE_URL: 'https://auth.midominio.co', VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK }).projectRef).toBe(
      'auth.midominio.co',
    );
  });

  it.each([
    ['faltan ambas', {}],
    ['falta la clave', { VITE_SUPABASE_URL: URL_OK }],
    ['HTTP', { VITE_SUPABASE_URL: 'http://proyectoprueba.supabase.co', VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK }],
    ['otro protocolo', { VITE_SUPABASE_URL: 'wss://proyectoprueba.supabase.co', VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK }],
    ['relativa', { VITE_SUPABASE_URL: 'proyectoprueba.supabase.co', VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK }],
    ['con credenciales', { VITE_SUPABASE_URL: 'https://usuario:clave@proyectoprueba.supabase.co', VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK }],
    ['con ruta', { VITE_SUPABASE_URL: `${URL_OK}/auth/v1/`, VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK }],
    ['con parámetros', { VITE_SUPABASE_URL: `${URL_OK}/?x=1`, VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK }],
    ['cadena arbitraria', { VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: 'cualquier-cosa' }],
    ['prefijo incompleto', { VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: 'sb_publishable_' }],
    ['clave secreta', { VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: 'sb_secret_prueba123' }],
    ['service_role', { VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: legacyKey({ role: 'service_role' }) }],
    ['JWT sin rol', { VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: legacyKey({ sub: 'x' }) }],
    ['JWT authenticated', { VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: legacyKey({ role: 'authenticated' }) }],
    ['anon de otro proyecto', { VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: legacyKey({ role: 'anon', ref: 'otro' }) }],
  ])('rechaza: %s', (_reason, env) => {
    expect(() => resolveSupabaseConfig(env)).toThrow(ConfigurationError);
  });

  it('solo lee el payload declarado, sin validar la firma', () => {
    expect(readLegacyJwtPayload(legacyKey({ role: 'anon' }))).toEqual({ role: 'anon' });
    expect(readLegacyJwtPayload('no.es-un.jwt!')).toBeNull();
  });
});

describe('assertPublicEnv (comprobación previa al build)', () => {
  it('permite compilar sin variables de Supabase', () => {
    expect(() => assertPublicEnv({ VITE_API_BASE_URL: '' })).not.toThrow();
  });

  it('permite una configuración válida', () => {
    expect(() => assertPublicEnv({ VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: KEY_OK })).not.toThrow();
  });

  it('detiene el build si la configuración está a medias o es inválida', () => {
    expect(() => assertPublicEnv({ VITE_SUPABASE_URL: URL_OK })).toThrow(ConfigurationError);
    expect(() => assertPublicEnv({ VITE_SUPABASE_URL: URL_OK, VITE_SUPABASE_PUBLISHABLE_KEY: 'x' })).toThrow(ConfigurationError);
  });

  it('detiene el build si cualquier VITE_* contiene una clave secreta', () => {
    expect(() => assertPublicEnv({ VITE_OTRA: 'sb_secret_prueba123' })).toThrow(/VITE_OTRA/);
    expect(() => assertPublicEnv({ VITE_OTRA: legacyKey({ role: 'service_role' }) })).toThrow(ConfigurationError);
  });

  it('ignora variables que no se incrustan en el bundle', () => {
    expect(() => assertPublicEnv({ SERVER_ONLY: 'sb_secret_prueba123' })).not.toThrow();
  });
});
