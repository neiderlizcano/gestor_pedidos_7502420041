import { inject, type InjectionKey } from 'vue';
import type { UseCases } from '../application/ports/in/use-cases';

export const USE_CASES: InjectionKey<UseCases> = Symbol('use-cases');

export function useApplication(): UseCases {
  const app = inject(USE_CASES);
  if (!app) throw new Error('Falta configurar los casos de uso.');
  return app;
}
