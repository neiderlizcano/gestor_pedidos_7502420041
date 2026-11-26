import { AuthError } from '../../domain/exceptions/AuthError';
import type { AuthSession, RemoteSignOut } from '../../domain/model/AuthSession';
import type { AuthGateway } from '../../domain/ports/out/AuthGateway';

const misconfigured = () => new AuthError('MISCONFIGURED', 'La autenticación no está configurada.');

// Sustituye al adaptador real cuando faltan la URL o la clave pública
export class MisconfiguredAuthGateway implements AuthGateway {
  async currentSession(): Promise<AuthSession | null> {
    throw misconfigured();
  }

  async signInWithPassword(): Promise<AuthSession> {
    throw misconfigured();
  }

  async signOut(): Promise<RemoteSignOut> {
    // No hay sesión que cerrar
    return 'notRequired';
  }

  onSessionChange(): () => void {
    return () => undefined;
  }
}
