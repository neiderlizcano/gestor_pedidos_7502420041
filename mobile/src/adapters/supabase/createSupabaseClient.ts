import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import type { SessionStore } from '../../domain/ports/out/SessionStore';

// Configuración que necesita el adaptador; la valida bootstrap/supabase-config
export interface SupabaseConfig {
  readonly url: string;
  readonly publishableKey: string;
  // Identificador del proyecto: separa las sesiones de proyectos distintos
  readonly projectRef: string;
}

const AUTH_STORAGE_PREFIX = 'gestor-pedidos-auth';

/**
 * Clave lógica de la sesión. En Android es estable para restaurarla tras reiniciar.
 * En el navegador lleva un identificador por pestaña: el SDK nombra su
 * BroadcastChannel con esta clave, así que pestañas distintas no comparten eventos
 * ni tokens (cada una guarda su sesión en memoria propia).
 */
export function authStorageKey(projectRef: string, tabId?: string): string {
  const base = `${AUTH_STORAGE_PREFIX}:${projectRef}`;
  return tabId ? `${base}:${tabId}` : base;
}

export interface SupabaseClientOptions {
  readonly storageKey: string;
  // Solo para pruebas con respuestas simuladas
  readonly fetch?: typeof fetch;
}

// El SDK guarda y renueva la sesión; aquí solo se elige dónde y con qué clave
export function createSupabaseClient(
  config: SupabaseConfig,
  store: SessionStore,
  options: SupabaseClientOptions,
): SupabaseClient {
  return createClient(config.url, config.publishableKey, {
    auth: {
      storage: store,
      storageKey: options.storageKey,
      persistSession: true,
      autoRefreshToken: true,
      // La app no recibe sesiones por URL (enlaces mágicos u OAuth)
      detectSessionInUrl: false,
      flowType: 'pkce',
      debug: false,
    },
    ...(options.fetch ? { global: { fetch: options.fetch } } : {}),
  });
}
