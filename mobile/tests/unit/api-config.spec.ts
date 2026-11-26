import { describe, expect, it } from 'vitest';
import {
  DEFAULT_NATIVE_API_BASE_URL, resolveApiBaseUrl, resolveNativeBaseUrl, resolveWebBaseUrl,
} from '../../src/bootstrap/api-config';
import { parsePublicOrigin } from '../../src/bootstrap/public-url';
import { ConfigurationError } from '../../src/domain/exceptions/errors';

// URL con contraseña sintética: nunca debe aparecer en mensajes de error
const WITH_PASSWORD = 'https://usuario:contrasena-sintetica@api.test';

const INVALID = [
  ['HTTP', 'http://api.test'],
  ['relativa', '/api'],
  ['sin protocolo', 'api.test'],
  ['otro protocolo', 'ftp://api.test'],
  ['usuario y contraseña', WITH_PASSWORD],
  ['solo usuario', 'https://usuario@api.test'],
  ['arroba vacía', 'https://@api.test'],
  ['parámetros', 'https://api.test/?x=1'],
  ['interrogación vacía', 'https://api.test?'],
  ['fragmento', 'https://api.test/#x'],
  ['ruta', 'https://api.test/base'],
  ['ruta con barra', 'https://api.test/api/'],
] as const;

describe('configuración de la API', () => {
  it('en web usa base vacía para pasar por el proxy de Vite (excepción de desarrollo)', () => {
    expect(resolveWebBaseUrl(undefined)).toBe('');
    expect(resolveWebBaseUrl('  ')).toBe('');
    expect(resolveApiBaseUrl(false, { VITE_NATIVE_API_BASE_URL: 'https://otro.test' })).toBe('');
  });

  it('acepta solo el origen HTTPS y lo normaliza', () => {
    expect(resolveWebBaseUrl('https://api.test/')).toBe('https://api.test');
    expect(resolveApiBaseUrl(true, { VITE_NATIVE_API_BASE_URL: 'https://api.test:8443' })).toBe('https://api.test:8443');
  });

  it('en Android usa la URL pública por defecto si no hay variable', () => {
    expect(resolveNativeBaseUrl(undefined)).toBe(DEFAULT_NATIVE_API_BASE_URL);
    expect(resolveNativeBaseUrl('')).toBe(DEFAULT_NATIVE_API_BASE_URL);
  });

  it.each(INVALID)('rechaza en ambas bases: %s', (_reason, value) => {
    expect(() => resolveNativeBaseUrl(value)).toThrow(ConfigurationError);
    expect(() => resolveWebBaseUrl(value)).toThrow(ConfigurationError);
  });

  it('el error nombra la variable pero no muestra el valor', () => {
    const error = (() => {
      try {
        parsePublicOrigin('VITE_API_BASE_URL', WITH_PASSWORD);
      } catch (e) {
        return e as Error;
      }
    })();
    expect(error?.message).toContain('VITE_API_BASE_URL');
    expect(`${error?.message} ${error?.stack}`).not.toContain('contrasena-sintetica');
  });
});
