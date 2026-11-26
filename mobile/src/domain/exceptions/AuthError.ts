export type AuthErrorCode =
  | 'INVALID_CREDENTIALS'
  | 'EMAIL_NOT_CONFIRMED'
  | 'RATE_LIMITED'
  | 'SESSION_EXPIRED'
  // Ya hay una sesión activa: hay que cerrarla antes de entrar con otra cuenta
  | 'CONFLICT'
  | 'NETWORK'
  | 'STORAGE'
  | 'MISCONFIGURED'
  | 'UNKNOWN';

// Error de autenticación sin datos sensibles: nunca incluye contraseñas ni tokens
export class AuthError extends Error {
  constructor(
    readonly code: AuthErrorCode,
    message: string = code,
  ) {
    super(message);
    this.name = 'AuthError';
  }
}

export const isAuthError = (error: unknown): error is AuthError => error instanceof AuthError;
