import { describe, expect, it } from 'vitest';
import { HealthApplicationService } from '../../src/application/services/HealthApplicationService';
import { isServiceAvailable, type HealthCheck, type ServiceState } from '../../src/domain/model/ApiStatus';
import type { HealthGateway } from '../../src/domain/ports/out/HealthGateway';

// Reloj falso para obtener latencias deterministas
const clock = (...ticks: number[]) => () => ticks.shift() ?? 0;
const gatewayReturning = (result: HealthCheck): HealthGateway => ({ check: async () => result });

describe('HealthApplicationService', () => {
  it('mide la latencia solo cuando el servicio está UP', async () => {
    const service = new HealthApplicationService(gatewayReturning({ state: 'UP', httpStatus: 200 }), clock(1000, 1250));
    const status = await service.checkApiHealth();
    expect(status).toMatchObject({ state: 'UP', httpStatus: 200, latencyMs: 250 });
    expect(status.checkedAt.getTime()).toBe(1250);
    expect(isServiceAvailable(status)).toBe(true);
  });

  it.each<[ServiceState, number | null]>([
    ['DOWN', 503],
    ['UNAVAILABLE', 503],
    ['NOT_FOUND', 404],
    ['HTTP_ERROR', 500],
    ['INVALID_RESPONSE', 200],
    ['TIMEOUT', null],
    ['CONNECTION_ERROR', null],
    ['MISCONFIGURED', null],
  ])('no informa latencia para %s', async (state, httpStatus) => {
    const service = new HealthApplicationService(gatewayReturning({ state, httpStatus }), clock(0, 40));
    const status = await service.checkApiHealth();
    expect(status).toMatchObject({ state, httpStatus, latencyMs: null });
    expect(isServiceAvailable(status)).toBe(false);
  });

  it('convierte un error inesperado del gateway en fallo de conexión', async () => {
    const gateway: HealthGateway = {
      check: async () => {
        throw new Error('fallo interno');
      },
    };
    const status = await new HealthApplicationService(gateway, clock(0, 10)).checkApiHealth();
    expect(status).toMatchObject({ state: 'CONNECTION_ERROR', httpStatus: null, latencyMs: null });
  });
});
