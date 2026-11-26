import { Capacitor } from '@capacitor/core';
import { HttpClient } from '../adapters/http/HttpClient';
import { HealthHttpAdapter } from '../adapters/http/HealthHttpAdapter';
import { MisconfiguredHealthGateway } from '../adapters/http/MisconfiguredHealthGateway';
import { HealthApplicationService } from '../application/services/HealthApplicationService';
import type { UseCases } from '../application/ports/in/use-cases';
import type { HealthGateway } from '../domain/ports/out/HealthGateway';
import { resolveApiBaseUrl } from './api-config';
import { readPublicEnv } from './public-env';

function createHealthGateway(): HealthGateway {
  try {
    const baseUrl = resolveApiBaseUrl(Capacitor.isNativePlatform(), readPublicEnv());
    return new HealthHttpAdapter(new HttpClient(baseUrl));
  } catch (error) {
    // Solo el nombre del error, nunca la configuración
    console.error('API sin configurar:', error instanceof Error ? error.name : 'Error');
    return new MisconfiguredHealthGateway();
  }
}

export const useCases: UseCases = new HealthApplicationService(createHealthGateway());
