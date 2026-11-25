import { ConfigurationError } from '../domain/exceptions/errors';

export const DEFAULT_NATIVE_API_BASE_URL = 'https://gestor-pedidos-api.onrender.com';

export interface ApiEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_NATIVE_API_BASE_URL?: string;
}

const trimSlash = (url: string) => url.replace(/\/+$/, '');

// En Android no existe el proxy de Vite: la URL debe ser HTTPS y absoluta
export function resolveNativeBaseUrl(raw: string | undefined): string {
  const value = raw?.trim() || DEFAULT_NATIVE_API_BASE_URL;
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new ConfigurationError('VITE_NATIVE_API_BASE_URL debe ser una URL absoluta.');
  }
  if (url.protocol !== 'https:') {
    throw new ConfigurationError('VITE_NATIVE_API_BASE_URL debe usar HTTPS.');
  }
  return trimSlash(url.origin + url.pathname);
}

// En el navegador, una base vacía deja que el proxy de Vite atienda /actuator y /api
export function resolveWebBaseUrl(raw: string | undefined): string {
  return trimSlash(raw?.trim() ?? '');
}

export function resolveApiBaseUrl(isNative: boolean, env: ApiEnv): string {
  return isNative ? resolveNativeBaseUrl(env.VITE_NATIVE_API_BASE_URL) : resolveWebBaseUrl(env.VITE_API_BASE_URL);
}
