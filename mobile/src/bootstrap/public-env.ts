import { resolveNativeBaseUrl, resolveWebBaseUrl } from './api-config';
import { assertPublicEnv, resolveSupabaseConfig } from './supabase-config';

/**
 * Lista cerrada de variables que pueden llegar al código del cliente.
 * Cualquier otra variable de entorno, aunque empiece por VITE_, no se publica.
 */
export const PUBLIC_ENV_KEYS = [
  'VITE_API_BASE_URL',
  'VITE_NATIVE_API_BASE_URL',
  'VITE_SUPABASE_URL',
  'VITE_SUPABASE_PUBLISHABLE_KEY',
] as const;

export type PublicEnvKey = (typeof PUBLIC_ENV_KEYS)[number];
export type PublicEnv = Partial<Record<PublicEnvKey, string>>;

export interface PublicEnvBuild {
  readonly publicEnv: PublicEnv;
  // Solo nombres, nunca valores: se pueden mostrar en la consola del build
  readonly ignoredNames: string[];
}

const present = (value: string | undefined) => Boolean(value?.trim());

/**
 * Se ejecuta en vite.config.ts antes de servir o compilar.
 * Valida cada variable autorizada y publica solo su forma normalizada; si algo es
 * inválido lanza ConfigurationError (con el nombre, nunca el valor) y Vite no arranca.
 */
export function buildPublicEnv(env: Record<string, string | undefined>): PublicEnvBuild {
  // Claves administrativas en cualquier VITE_* y configuración de Supabase a medias o inválida
  assertPublicEnv(env);

  const publicEnv: PublicEnv = {};
  if (present(env.VITE_API_BASE_URL)) publicEnv.VITE_API_BASE_URL = resolveWebBaseUrl(env.VITE_API_BASE_URL);
  if (present(env.VITE_NATIVE_API_BASE_URL)) {
    publicEnv.VITE_NATIVE_API_BASE_URL = resolveNativeBaseUrl(env.VITE_NATIVE_API_BASE_URL);
  }
  if (present(env.VITE_SUPABASE_URL) || present(env.VITE_SUPABASE_PUBLISHABLE_KEY)) {
    const supabase = resolveSupabaseConfig(env);
    publicEnv.VITE_SUPABASE_URL = supabase.url;
    publicEnv.VITE_SUPABASE_PUBLISHABLE_KEY = supabase.publishableKey;
  }

  const allowed = new Set<string>(PUBLIC_ENV_KEYS);
  const ignoredNames = Object.keys(env)
    .filter((name) => name.startsWith('VITE_') && !allowed.has(name))
    .sort();
  return { publicEnv, ignoredNames };
}

// Lectura en el cliente; fuera de Vite (por ejemplo, en pruebas) no hay configuración
export function readPublicEnv(): PublicEnv {
  return typeof __GESTOR_PUBLIC_ENV__ === 'undefined' ? {} : __GESTOR_PUBLIC_ENV__;
}
