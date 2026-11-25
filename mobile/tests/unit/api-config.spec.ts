import { describe, expect, it } from 'vitest';
import {
  DEFAULT_NATIVE_API_BASE_URL, resolveApiBaseUrl, resolveNativeBaseUrl, resolveWebBaseUrl,
} from '../../src/bootstrap/api-config';
import { ConfigurationError } from '../../src/domain/exceptions/errors';

describe('configuración de la API', () => {
  it('en web usa base vacía para pasar por el proxy de Vite', () => {
    expect(resolveWebBaseUrl(undefined)).toBe('');
    expect(resolveWebBaseUrl('  ')).toBe('');
    expect(resolveApiBaseUrl(false, { VITE_NATIVE_API_BASE_URL: 'https://otro.test' })).toBe('');
  });

  it('en web respeta una base explícita sin barra final', () => {
    expect(resolveWebBaseUrl('https://api.test/')).toBe('https://api.test');
  });

  it('en Android usa la URL HTTPS absoluta configurada', () => {
    expect(resolveApiBaseUrl(true, { VITE_NATIVE_API_BASE_URL: 'https://api.test/base/' })).toBe(
      'https://api.test/base',
    );
  });

  it('en Android usa la URL pública por defecto si no hay variable', () => {
    expect(resolveNativeBaseUrl(undefined)).toBe(DEFAULT_NATIVE_API_BASE_URL);
    expect(resolveNativeBaseUrl('')).toBe(DEFAULT_NATIVE_API_BASE_URL);
  });

  it.each(['http://api.test', '/api', 'api.test', 'ftp://api.test'])('en Android rechaza %s', (value) => {
    expect(() => resolveNativeBaseUrl(value)).toThrow(ConfigurationError);
  });
});
