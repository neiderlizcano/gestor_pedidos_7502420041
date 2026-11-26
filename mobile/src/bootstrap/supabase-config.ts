import type { SupabaseConfig } from '../adapters/supabase/createSupabaseClient';
import { ConfigurationError } from '../domain/exceptions/errors';
import { parsePublicOrigin } from './public-url';

export interface SupabaseEnv {
  readonly VITE_SUPABASE_URL?: string;
  readonly VITE_SUPABASE_PUBLISHABLE_KEY?: string;
}

const PUBLISHABLE_KEY = /^sb_publishable_[A-Za-z0-9_-]{8,}$/;
const JWT_SHAPE = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

interface LegacyJwtPayload {
  readonly role?: unknown;
  readonly ref?: unknown;
}

function decodeBase64Url(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
  return atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='));
}

/**
 * Lee el payload de una clave JWT heredada. Solo sirve para saber qué tipo de clave
 * declara (anon o service_role); no comprueba la firma ni prueba que sea auténtica.
 */
export function readLegacyJwtPayload(key: string): LegacyJwtPayload | null {
  if (!JWT_SHAPE.test(key)) return null;
  try {
    const payload: unknown = JSON.parse(decodeBase64Url(key.split('.')[1]));
    return typeof payload === 'object' && payload !== null ? (payload as LegacyJwtPayload) : null;
  } catch {
    return null;
  }
}

// Detecta valores que nunca deben llegar al cliente
export function looksLikeSecretKey(value: string): boolean {
  if (value.includes('sb_secret_')) return true;
  return value
    .split(/\s+/)
    .some((part) => readLegacyJwtPayload(part)?.role === 'service_role');
}

function projectRefFromHost(host: string): string {
  // Proyectos alojados: <ref>.supabase.co; dominios propios: el host completo
  const match = /^([a-z0-9]+)\.supabase\.co$/.exec(host);
  return match ? match[1] : host.replace(/[^a-z0-9.-]/g, '');
}

// Se espera la raíz del proyecto, no la ruta /auth/v1 que usa el cliente Kotlin
function validateUrl(raw: string): URL {
  return new URL(parsePublicOrigin('VITE_SUPABASE_URL', raw));
}

function validateKey(key: string, projectRef: string): void {
  if (key.startsWith('sb_secret_') || looksLikeSecretKey(key)) {
    throw new ConfigurationError('VITE_SUPABASE_PUBLISHABLE_KEY no puede ser una clave secreta.');
  }
  if (PUBLISHABLE_KEY.test(key)) return;

  const payload = readLegacyJwtPayload(key);
  if (payload?.role !== 'anon') {
    throw new ConfigurationError('VITE_SUPABASE_PUBLISHABLE_KEY debe ser sb_publishable_… o una clave anon heredada.');
  }
  // Una clave anon de otro proyecto indicaría una configuración mezclada
  if (typeof payload.ref === 'string' && payload.ref !== projectRef) {
    throw new ConfigurationError('La clave anon pertenece a otro proyecto de Supabase.');
  }
}

// Lanza ConfigurationError si falta o es inválida (la app queda en MISCONFIGURED)
export function resolveSupabaseConfig(env: SupabaseEnv): SupabaseConfig {
  const rawUrl = env.VITE_SUPABASE_URL?.trim();
  const key = env.VITE_SUPABASE_PUBLISHABLE_KEY?.trim();
  if (!rawUrl || !key) {
    throw new ConfigurationError('Faltan VITE_SUPABASE_URL o VITE_SUPABASE_PUBLISHABLE_KEY.');
  }
  const url = validateUrl(rawUrl);
  const projectRef = projectRefFromHost(url.hostname);
  validateKey(key, projectRef);
  return { url: url.origin, publishableKey: key, projectRef };
}

/**
 * Comprobación previa al build: las variables VITE_* se incrustan en el bundle.
 * Falla si alguna contiene una clave secreta, o si la configuración de Supabase
 * está a medias o es inválida. Sin variables de Supabase se permite compilar.
 */
export function assertPublicEnv(env: Record<string, string | undefined>): void {
  for (const [name, value] of Object.entries(env)) {
    if (name.startsWith('VITE_') && value && looksLikeSecretKey(value)) {
      throw new ConfigurationError(`${name} contiene una clave secreta y no puede incluirse en la app.`);
    }
  }
  const hasUrl = Boolean(env.VITE_SUPABASE_URL?.trim());
  const hasKey = Boolean(env.VITE_SUPABASE_PUBLISHABLE_KEY?.trim());
  if (hasUrl || hasKey) resolveSupabaseConfig(env);
}
