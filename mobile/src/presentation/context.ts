import { inject, type InjectionKey } from 'vue';
import type { AuthUseCases } from '../application/ports/in/auth-use-cases';
import type { UseCases } from '../application/ports/in/use-cases';

export const USE_CASES: InjectionKey<UseCases> = Symbol('use-cases');
export const AUTH_USE_CASES: InjectionKey<AuthUseCases> = Symbol('auth-use-cases');

export function useApplication(): UseCases {
  const app = inject(USE_CASES);
  if (!app) throw new Error('Falta configurar los casos de uso.');
  return app;
}

// Lo usarán las pantallas de inicio de sesión (C4-03C)
export function useAuth(): AuthUseCases {
  const auth = inject(AUTH_USE_CASES);
  if (!auth) throw new Error('Falta configurar la autenticación.');
  return auth;
}
