import { registerPlugin } from '@capacitor/core';
import { AuthError } from '../../domain/exceptions/AuthError';
import type { SessionStore } from '../../domain/ports/out/SessionStore';

// Contrato del plugin local SecureSessionStorage (mobile/android/.../securestorage)
export interface SecureSessionStoragePlugin {
  // Nunca devuelve un valor revocado por una marca de invalidación
  get(options: { key: string }): Promise<{ value: string | null; recovered: boolean }>;
  // Resuelve solo si el valor se releyó y coincide
  set(options: { key: string; value: string }): Promise<void>;
  // Resuelve si el valor ya no puede restaurarse; pendingCleanup indica borrado físico pendiente
  remove(options: { key: string }): Promise<{ pendingCleanup: boolean }>;
}

export const SecureSessionStorage = registerPlugin<SecureSessionStoragePlugin>('SecureSessionStorage');

const storageError = () => new AuthError('STORAGE', 'No se pudo confirmar la operación en el almacenamiento seguro.');

// Android: cifra con una clave del Keystore y guarda fuera de las copias de seguridad
export class NativeSecureSessionStore implements SessionStore {
  constructor(private readonly plugin: SecureSessionStoragePlugin = SecureSessionStorage) {}

  async getItem(key: string): Promise<string | null> {
    try {
      // Si el archivo estaba dañado o revocado, el plugin responde null
      const { value } = await this.plugin.get({ key });
      return value;
    } catch {
      throw storageError();
    }
  }

  async setItem(key: string, value: string): Promise<void> {
    try {
      await this.plugin.set({ key, value });
    } catch {
      throw storageError();
    }
  }

  async removeItem(key: string): Promise<void> {
    try {
      await this.plugin.remove({ key });
    } catch {
      // Ni la marca ni el borrado se confirmaron
      throw storageError();
    }
  }
}
