import { AuthError, isAuthError } from '../../domain/exceptions/AuthError';
import type { AuthSession, RemoteSignOut, SessionState, SignOutResult } from '../../domain/model/AuthSession';
import type { AuthGateway } from '../../domain/ports/out/AuthGateway';
import type { AuthUseCases, SessionStateListener } from '../ports/in/auth-use-cases';

const toState = (session: AuthSession | null): SessionState =>
  session ? { status: 'signedIn', session } : { status: 'signedOut' };

const storageError = () => new AuthError('STORAGE', 'No se pudo confirmar el almacenamiento de la sesión.');

/**
 * Coordina el estado de sesión.
 * - Las operaciones (inicializar, entrar, salir, reconciliar) se ejecutan en cola,
 *   una tras otra, para que el SDK nunca escriba dos sesiones a la vez.
 * - El estado visible se deriva siempre de lo guardado (currentSession), nunca
 *   directamente de un evento del SDK.
 * - Si no se puede confirmar un borrado, el estado queda 'unavailable' y no se
 *   vuelve a restaurar la sesión antigua hasta confirmar la invalidación.
 */
export class AuthApplicationService implements AuthUseCases {
  private current: SessionState = { status: 'initializing' };
  private queue: Promise<unknown> = Promise.resolve();
  private readonly listeners = new Set<SessionStateListener>();
  private unsubscribeGateway: (() => void) | null = null;
  // Hay un cierre de sesión sin confirmar en este proceso
  private pendingInvalidation = false;

  constructor(private readonly gateway: AuthGateway) {}

  get state(): SessionState {
    return this.current;
  }

  initialize(): Promise<SessionState> {
    this.unsubscribeGateway ??= this.gateway.onSessionChange(() => {
      // El aviso solo programa una lectura; el estado sale de lo guardado
      void this.enqueue(() => this.reconcile()).catch(() => undefined);
    });
    return this.enqueue(() => this.reconcile());
  }

  signIn(email: string, password: string): Promise<SessionState> {
    return this.enqueue(async () => {
      if (this.current.status === 'initializing') await this.reconcile().catch(() => undefined);
      if (this.pendingInvalidation) throw storageError();
      if (this.current.status === 'signedIn') {
        throw new AuthError('CONFLICT', 'Cierra la sesión actual antes de entrar con otra cuenta.');
      }
      let session: AuthSession;
      try {
        session = await this.gateway.signInWithPassword(email, password);
      } catch (error) {
        // Una escritura a medias deja datos sin confirmar: se exige invalidarlos
        if (isAuthError(error) && error.code === 'STORAGE') this.markStorageFailure();
        throw error;
      }
      // Solo se informa éxito si lo guardado corresponde a esta cuenta
      const stored = await this.readStored().catch(() => undefined);
      if (!stored || stored.user.id !== session.user.id) {
        this.markStorageFailure();
        throw storageError();
      }
      return this.apply(toState(stored));
    });
  }

  signOut(): Promise<SignOutResult> {
    return this.enqueue(async () => {
      this.pendingInvalidation = true;
      let remoteSignOut: RemoteSignOut;
      try {
        remoteSignOut = await this.gateway.signOut();
      } catch (error) {
        // Solo llega aquí un fallo del borrado local: se mantiene el bloqueo
        this.apply({ status: 'unavailable', reason: 'STORAGE' });
        throw isAuthError(error) && error.code === 'STORAGE' ? error : storageError();
      }
      // El cierre se confirma leyendo de nuevo lo guardado
      let stored: AuthSession | null;
      try {
        stored = await this.readStored();
      } catch (error) {
        this.apply({ status: 'unavailable', reason: isAuthError(error) ? error.code : 'STORAGE' });
        throw isAuthError(error) ? error : storageError();
      }
      if (stored !== null) {
        this.apply({ status: 'unavailable', reason: 'STORAGE' });
        throw storageError();
      }
      this.pendingInvalidation = false;
      this.apply({ status: 'signedOut' });
      // Con 'unconfirmed' la sesión local está cerrada, pero el servidor no confirmó la revocación
      return { localSession: 'closedAndVerified', remoteSignOut };
    });
  }

  subscribe(listener: SessionStateListener): () => void {
    this.listeners.add(listener);
    listener(this.current);
    return () => this.listeners.delete(listener);
  }

  // Alinea el estado visible con lo guardado
  private async reconcile(): Promise<SessionState> {
    if (this.pendingInvalidation) {
      // Tras un cierre fallido no se restaura nada: se reintenta la invalidación
      try {
        await this.gateway.signOut();
      } catch {
        return this.apply({ status: 'unavailable', reason: 'STORAGE' });
      }
    }
    let stored: AuthSession | null;
    try {
      stored = await this.gateway.currentSession();
    } catch (error) {
      const reason = isAuthError(error) ? error.code : 'UNKNOWN';
      this.apply({ status: 'unavailable', reason });
      throw error;
    }
    if (this.pendingInvalidation) {
      if (stored) return this.apply({ status: 'unavailable', reason: 'STORAGE' });
      this.pendingInvalidation = false;
    }
    return this.apply(toState(stored));
  }

  private async readStored(): Promise<AuthSession | null> {
    return this.gateway.currentSession();
  }

  private markStorageFailure(): void {
    this.pendingInvalidation = true;
    this.apply({ status: 'unavailable', reason: 'STORAGE' });
  }

  private enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const run = this.queue.then(operation, operation);
    this.queue = run.catch(() => undefined);
    return run;
  }

  private apply(next: SessionState): SessionState {
    if (sameState(this.current, next)) return this.current;
    this.current = next;
    for (const listener of this.listeners) listener(next);
    return next;
  }
}

function sameState(a: SessionState, b: SessionState): boolean {
  if (a.status !== b.status) return false;
  if (a.status === 'signedIn' && b.status === 'signedIn') {
    return a.session.user.id === b.session.user.id && a.session.expiresAt === b.session.expiresAt;
  }
  if (a.status === 'unavailable' && b.status === 'unavailable') return a.reason === b.reason;
  return true;
}
