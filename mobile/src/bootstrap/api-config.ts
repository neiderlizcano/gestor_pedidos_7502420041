import { parsePublicOrigin } from './public-url';

export const DEFAULT_NATIVE_API_BASE_URL = 'https://gestor-pedidos-api.onrender.com';

export interface ApiEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_NATIVE_API_BASE_URL?: string;
}

// En Android no existe el proxy de Vite: origen HTTPS absoluto
export function resolveNativeBaseUrl(raw: string | undefined): string {
  return parsePublicOrigin('VITE_NATIVE_API_BASE_URL', raw?.trim() || DEFAULT_NATIVE_API_BASE_URL);
}

// En el navegador, una base vacía deja que el proxy de Vite atienda /actuator y /api
// (única excepción de desarrollo local); cualquier otro valor debe ser un origen HTTPS
export function resolveWebBaseUrl(raw: string | undefined): string {
  const value = raw?.trim() ?? '';
  return value ? parsePublicOrigin('VITE_API_BASE_URL', value) : '';
}

export function resolveApiBaseUrl(isNative: boolean, env: ApiEnv): string {
  return isNative ? resolveNativeBaseUrl(env.VITE_NATIVE_API_BASE_URL) : resolveWebBaseUrl(env.VITE_API_BASE_URL);
}
