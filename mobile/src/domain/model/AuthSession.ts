import type { AuthErrorCode } from '../exceptions/AuthError';

// Datos de sesión que necesita la app; los tokens se quedan en el adaptador
export interface AuthUser {
  readonly id: string;
  readonly email: string | null;
}

export interface AuthSession {
  readonly user: AuthUser;
  // Vencimiento del token de acceso en milisegundos desde época
  readonly expiresAt: number | null;
}

export type SessionState =
  | { readonly status: 'initializing' }
  | { readonly status: 'signedOut' }
  | { readonly status: 'signedIn'; readonly session: AuthSession }
  // No se pudo confirmar el estado guardado (red o almacenamiento): sin acceso privado
  | { readonly status: 'unavailable'; readonly reason: AuthErrorCode };

/**
 * Qué se sabe de la revocación en el servidor una vez cerrada la sesión local:
 * - confirmed: hay evidencia fiable de que el servidor revocó la sesión (invalida el refresh
 *   token). Aun así, los JWT de acceso ya emitidos siguen siendo válidos hasta que vencen.
 * - unconfirmed: no se puede verificar (error, sin red, o una respuesta que el proveedor no expone).
 * - notRequired: no había sesión que revocar.
 * El fallo del borrado local no es un valor de este tipo: se informa como AuthError STORAGE.
 */
export type RemoteSignOut = 'confirmed' | 'unconfirmed' | 'notRequired';

export interface SignOutResult {
  // Solo se devuelve cuando lo guardado se comprobó vacío: sesión local cerrada y verificada
  readonly localSession: 'closedAndVerified';
  readonly remoteSignOut: RemoteSignOut;
}

export const isSignedIn = (state: SessionState): state is Extract<SessionState, { status: 'signedIn' }> =>
  state.status === 'signedIn';
