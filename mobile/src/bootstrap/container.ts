import { Capacitor } from '@capacitor/core';
import { HttpClient } from '../adapters/http/HttpClient';
import { HealthHttpAdapter } from '../adapters/http/HealthHttpAdapter';
import { MisconfiguredHealthGateway } from '../adapters/http/MisconfiguredHealthGateway';
import { HealthApplicationService } from '../application/services/HealthApplicationService';
import type { UseCases } from '../application/ports/in/use-cases';
import type { HealthGateway } from '../domain/ports/out/HealthGateway';
import { resolveApiBaseUrl } from './api-config';

function createHealthGateway(): HealthGateway {
  try {
    const baseUrl = resolveApiBaseUrl(Capacitor.isNativePlatform(), import.meta.env);
    return new HealthHttpAdapter(new HttpClient(baseUrl));
  } catch (error) {
    console.error(error);
    return new MisconfiguredHealthGateway();
  }
}

export const useCases: UseCases = new HealthApplicationService(createHealthGateway());
