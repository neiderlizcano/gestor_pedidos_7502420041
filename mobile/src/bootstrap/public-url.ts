import { ConfigurationError } from '../domain/exceptions/errors';

/**
 * Valida una URL pública de configuración y devuelve solo su origen.
 * Exige HTTPS y rechaza credenciales, rutas, parámetros y fragmentos.
 * Los mensajes nombran la variable, nunca su valor.
 */
export function parsePublicOrigin(name: string, raw: string): string {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new ConfigurationError(`${name} debe ser una URL absoluta.`);
  }
  if (url.protocol !== 'https:') throw new ConfigurationError(`${name} debe usar HTTPS.`);
  // '@', '?' y '#' se revisan en el texto original: URL normaliza "https://host?" sin dejar rastro
  const afterScheme = raw.slice(raw.indexOf('//') + 2);
  if (url.username || url.password || afterScheme.includes('@')) {
    throw new ConfigurationError(`${name} no puede incluir usuario ni contraseña.`);
  }
  if (url.search || url.hash || /[?#]/.test(afterScheme)) {
    throw new ConfigurationError(`${name} no puede incluir parámetros ni fragmentos.`);
  }
  // La app añade sus propias rutas (/actuator, /api, /auth/v1): la base debe ser la raíz
  if (url.pathname !== '/') throw new ConfigurationError(`${name} debe ser solo el origen, sin ruta.`);
  return url.origin;
}
