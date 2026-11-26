import type { SessionState, SignOutResult } from '../../../domain/model/AuthSession';

export type SessionStateListener = (state: SessionState) => void;

// Puerto de entrada de autenticación que usarán las pantallas
export interface AuthUseCases {
  readonly state: SessionState;
  initialize(): Promise<SessionState>;
  signIn(email: string, password: string): Promise<SessionState>;
  // Resuelve cuando la sesión local se confirmó cerrada; el resultado dice qué pasó en el servidor
  signOut(): Promise<SignOutResult>;
  subscribe(listener: SessionStateListener): () => void;
}
