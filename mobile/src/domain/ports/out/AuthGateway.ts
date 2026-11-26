import type { AuthSession, RemoteSignOut } from '../../model/AuthSession';

// Aviso de que la sesión guardada pudo cambiar (renovación, cierre, otra cuenta)
export type SessionChangeListener = () => void;

// Puerto hacia el proveedor de identidad. Los fallos se lanzan como AuthError
export interface AuthGateway {
  // Sesión guardada según el almacenamiento (el proveedor la renueva si hace falta)
  currentSession(): Promise<AuthSession | null>;
  signInWithPassword(email: string, password: string): Promise<AuthSession>;
  /**
   * Borra la sesión de este dispositivo e intenta revocarla en el servidor.
   * Lanza STORAGE si el borrado local falló; un fallo remoto no lanza, se informa en el resultado.
   */
  signOut(): Promise<RemoteSignOut>;
  onSessionChange(listener: SessionChangeListener): () => void;
}
