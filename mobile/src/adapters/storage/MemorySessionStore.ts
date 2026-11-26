import type { SessionStore } from '../../domain/ports/out/SessionStore';

// Navegador: la sesión vive solo mientras la pestaña está abierta.
// No se usa localStorage ni sessionStorage para no dejar tokens en el perfil del navegador.
export class MemorySessionStore implements SessionStore {
  private readonly items = new Map<string, string>();

  async getItem(key: string): Promise<string | null> {
    return this.items.get(key) ?? null;
  }

  async setItem(key: string, value: string): Promise<void> {
    this.items.set(key, value);
  }

  async removeItem(key: string): Promise<void> {
    this.items.delete(key);
  }
}
