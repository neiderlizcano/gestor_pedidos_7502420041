import { isAuthApiError, isAuthError as isSupabaseAuthError, isAuthRetryableFetchError } from '@supabase/supabase-js';
import { AuthError, isAuthError } from '../../domain/exceptions/AuthError';

const SESSION_CODES = new Set([
  'session_expired',
  'session_not_found',
  'refresh_token_not_found',
  'refresh_token_already_used',
  'bad_jwt',
]);

// Traduce errores del SDK sin copiar su mensaje, que podría incluir datos de la cuenta
export function toAuthError(error: unknown): AuthError {
  if (isAuthError(error)) return error;
  if (isAuthRetryableFetchError(error)) return new AuthError('NETWORK', 'No se pudo contactar al servicio de autenticación.');
  if (!isSupabaseAuthError(error)) return new AuthError('UNKNOWN', 'Error inesperado de autenticación.');

  const code = error.code ?? '';
  if (code === 'invalid_credentials' || error.name === 'AuthInvalidCredentialsError') {
    return new AuthError('INVALID_CREDENTIALS', 'Correo o contraseña incorrectos.');
  }
  if (code === 'email_not_confirmed') return new AuthError('EMAIL_NOT_CONFIRMED', 'El correo aún no está confirmado.');
  if (error.status === 429 || code.startsWith('over_')) {
    return new AuthError('RATE_LIMITED', 'Demasiados intentos. Espera un momento.');
  }
  // AuthRefreshDiscardedError: el commit guard del SDK descartó una renovación porque se cerró sesión
  if (SESSION_CODES.has(code) || error.name === 'AuthSessionMissingError' || error.name === 'AuthRefreshDiscardedError') {
    return new AuthError('SESSION_EXPIRED', 'La sesión expiró.');
  }
  // Versiones antiguas del servidor no envían código para credenciales inválidas
  if (isAuthApiError(error) && error.status === 400 && /invalid login credentials/i.test(error.message)) {
    return new AuthError('INVALID_CREDENTIALS', 'Correo o contraseña incorrectos.');
  }
  return new AuthError('UNKNOWN', 'Error inesperado de autenticación.');
}
