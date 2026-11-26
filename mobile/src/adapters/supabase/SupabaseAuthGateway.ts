import type { Session, SupabaseClient } from '@supabase/supabase-js';
import { AuthError } from '../../domain/exceptions/AuthError';
import type { AuthSession, RemoteSignOut } from '../../domain/model/AuthSession';
import type { AuthGateway, SessionChangeListener } from '../../domain/ports/out/AuthGateway';
import { toAuthError } from './supabase-errors';

// Subconjunto del cliente que usa el adaptador; facilita las pruebas
export type SupabaseAuthApi = Pick<
  SupabaseClient['auth'],
  'getSession' | 'signInWithPassword' | 'signOut' | 'onAuthStateChange'
>;

// Solo se copian identificador, correo y vencimiento; los tokens no salen del SDK
export function toDomainSession(session: Session | null): AuthSession | null {
  if (!session?.user) return null;
  return {
    user: { id: session.user.id, email: session.user.email ?? null },
    expiresAt: typeof session.expires_at === 'number' ? session.expires_at * 1000 : null,
  };
}

export class SupabaseAuthGateway implements AuthGateway {
  constructor(private readonly auth: SupabaseAuthApi) {}

  async currentSession(): Promise<AuthSession | null> {
    let result;
    try {
      result = await this.auth.getSession();
    } catch (error) {
      throw toAuthError(error);
    }
    if (result.error) {
      const mapped = toAuthError(result.error);
      // Refresh token rechazado o descartado: el SDK ya borró la sesión
      if (mapped.code === 'SESSION_EXPIRED') return null;
      throw mapped;
    }
    return toDomainSession(result.data.session);
  }

  async signInWithPassword(email: string, password: string): Promise<AuthSession> {
    let result;
    try {
      result = await this.auth.signInWithPassword({ email, password });
    } catch (error) {
      throw toAuthError(error);
    }
    if (result.error) throw toAuthError(result.error);
    const session = toDomainSession(result.data.session);
    if (!session) throw new AuthError('UNKNOWN', 'El servicio no devolvió una sesión.');
    return session;
  }

  async signOut(): Promise<RemoteSignOut> {
    // Se consulta antes para distinguir "no había nada que revocar"
    let hadSession = true;
    try {
      hadSession = (await this.auth.getSession()).data.session !== null;
    } catch {
      // Si no se puede saber, se asume que había sesión
    }

    // 'local' borra la sesión de este dispositivo antes de llamar al servidor.
    // Si ese borrado falla, el SDK relanza el error del almacenamiento: es un fallo local.
    let result;
    try {
      result = await this.auth.signOut({ scope: 'local' });
    } catch (error) {
      throw toAuthError(error);
    }
    // Un error devuelto (429, 5xx, sin red) es del servidor: lo local ya se borró.
    // No se reclasifica como STORAGE; el servicio comprueba igualmente lo guardado.
    if (!hadSession && !result.error) return 'notRequired';
    // Sin error tampoco hay confirmación: supabase-js 2.119 trata 401, 403 y 404 de /logout
    // como éxito y no expone el código HTTP. Sin evidencia fiable, se informa no confirmado.
    return 'unconfirmed';
  }

  onSessionChange(listener: SessionChangeListener): () => void {
    // El evento solo avisa; nunca se usa la sesión que trae (puede llegar tarde)
    const { data } = this.auth.onAuthStateChange((event) => {
      if (event !== 'INITIAL_SESSION') listener();
    });
    return () => data.subscription.unsubscribe();
  }
}
