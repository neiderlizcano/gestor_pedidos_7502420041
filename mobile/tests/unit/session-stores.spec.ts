import { describe, expect, it, vi } from 'vitest';
import { MemorySessionStore } from '../../src/adapters/storage/MemorySessionStore';
import {
  NativeSecureSessionStore, type SecureSessionStoragePlugin,
} from '../../src/adapters/storage/NativeSecureSessionStore';

describe('MemorySessionStore', () => {
  it('guarda, lee y elimina sin tocar el almacenamiento del navegador', async () => {
    const store = new MemorySessionStore();
    expect(await store.getItem('k')).toBeNull();
    await store.setItem('k', 'v');
    expect(await store.getItem('k')).toBe('v');
    await store.removeItem('k');
    expect(await store.getItem('k')).toBeNull();
  });

  it('cada instancia empieza vacía, como una pestaña recién abierta', async () => {
    await new MemorySessionStore().setItem('k', 'v');
    expect(await new MemorySessionStore().getItem('k')).toBeNull();
  });
});

describe('NativeSecureSessionStore', () => {
  const plugin = (overrides: Partial<SecureSessionStoragePlugin> = {}): SecureSessionStoragePlugin => ({
    get: vi.fn(async () => ({ value: 'v', recovered: false })),
    set: vi.fn(async () => undefined),
    remove: vi.fn(async () => ({ pendingCleanup: false })),
    ...overrides,
  });

  it('delega en el plugin nativo', async () => {
    const native = plugin();
    const store = new NativeSecureSessionStore(native);
    expect(await store.getItem('k')).toBe('v');
    await store.setItem('k', 'nuevo');
    await store.removeItem('k');
    expect(native.set).toHaveBeenCalledWith({ key: 'k', value: 'nuevo' });
    expect(native.remove).toHaveBeenCalledWith({ key: 'k' });
  });

  it('devuelve null cuando el plugin descartó datos dañados', async () => {
    const store = new NativeSecureSessionStore(plugin({ get: vi.fn(async () => ({ value: null, recovered: true })) }));
    expect(await store.getItem('k')).toBeNull();
  });

  it('convierte fallos del plugin en AuthError STORAGE sin exponer el valor', async () => {
    const failing = vi.fn(async () => Promise.reject(new Error('detalle interno')));
    const store = new NativeSecureSessionStore(plugin({ get: failing, set: failing, remove: failing }));
    await expect(store.getItem('k')).rejects.toMatchObject({ code: 'STORAGE' });
    const error = await store.setItem('k', 'token-secreto').catch((e: Error) => e);
    expect(error).toMatchObject({ code: 'STORAGE' });
    expect(String((error as Error).message)).not.toContain('token-secreto');
    await expect(store.removeItem('k')).rejects.toMatchObject({ code: 'STORAGE' });
  });

  it('acepta un borrado con limpieza pendiente: el plugin ya confirmó la invalidación', async () => {
    const store = new NativeSecureSessionStore(plugin({ remove: vi.fn(async () => ({ pendingCleanup: true })) }));
    await expect(store.removeItem('k')).resolves.toBeUndefined();
  });
});
