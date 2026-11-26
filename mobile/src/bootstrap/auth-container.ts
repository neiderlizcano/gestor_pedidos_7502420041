import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import type { SupabaseClient } from '@supabase/supabase-js';
import { MemorySessionStore } from '../adapters/storage/MemorySessionStore';
import { NativeSecureSessionStore } from '../adapters/storage/NativeSecureSessionStore';
import { authStorageKey, createSupabaseClient } from '../adapters/supabase/createSupabaseClient';
import { MisconfiguredAuthGateway } from '../adapters/supabase/MisconfiguredAuthGateway';
import { SupabaseAuthGateway } from '../adapters/supabase/SupabaseAuthGateway';
import type { AuthUseCases } from '../application/ports/in/auth-use-cases';
import { AuthApplicationService } from '../application/services/AuthApplicationService';
import type { AuthGateway } from '../domain/ports/out/AuthGateway';
import { readPublicEnv } from './public-env';
import { resolveSupabaseConfig } from './supabase-config';

const isNative = Capacitor.isNativePlatform();

// La renovación la hace el propio SDK; en Android solo se pausa en segundo plano
function bindAutoRefreshToAppState(client: SupabaseClient): void {
  if (!isNative) return;
  void App.addListener('appStateChange', ({ isActive }) => {
    if (isActive) void client.auth.startAutoRefresh();
    else void client.auth.stopAutoRefresh();
  });
}

function createAuthGateway(): AuthGateway {
  try {
    // Solo la lista cerrada de variables públicas, ya validada en el build
    const config = resolveSupabaseConfig(readPublicEnv());
    const client = isNative
      ? createSupabaseClient(config, new NativeSecureSessionStore(), {
          storageKey: authStorageKey(config.projectRef),
        })
      : // Navegador: memoria propia y clave única por pestaña
        createSupabaseClient(config, new MemorySessionStore(), {
          storageKey: authStorageKey(config.projectRef, crypto.randomUUID()),
        });
    bindAutoRefreshToAppState(client);
    return new SupabaseAuthGateway(client.auth);
  } catch (error) {
    // Solo el nombre del error, nunca la configuración
    console.error('Autenticación sin configurar:', error instanceof Error ? error.name : 'Error');
    return new MisconfiguredAuthGateway();
  }
}

export const authUseCases: AuthUseCases = new AuthApplicationService(createAuthGateway());
