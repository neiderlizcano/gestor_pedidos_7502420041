import { describe, expect, it } from 'vitest';
import type { ServiceState } from '../../src/domain/model/ApiStatus';
import { describeHealth } from '../../src/presentation/health-messages';

const ALL_STATES: ServiceState[] = [
  'UP', 'DOWN', 'UNAVAILABLE', 'NOT_FOUND', 'HTTP_ERROR', 'INVALID_RESPONSE', 'TIMEOUT', 'CONNECTION_ERROR', 'MISCONFIGURED',
];

describe('describeHealth', () => {
  it('ofrece un mensaje distinto para cada estado', () => {
    const titles = ALL_STATES.map((state) => describeHealth({ state, httpStatus: null }).title);
    expect(new Set(titles).size).toBe(ALL_STATES.length);
  });

  it('solo marca como éxito el estado UP', () => {
    for (const state of ALL_STATES) {
      const { tone } = describeHealth({ state, httpStatus: null });
      expect(tone === 'success').toBe(state === 'UP');
    }
  });

  it('añade el código HTTP a los fallos', () => {
    expect(describeHealth({ state: 'UNAVAILABLE', httpStatus: 503 }).title).toBe('Servicio no disponible (HTTP 503)');
    expect(describeHealth({ state: 'NOT_FOUND', httpStatus: 404 }).title).toBe('Ruta no encontrada (HTTP 404)');
    expect(describeHealth({ state: 'UP', httpStatus: 200 }).title).toBe('Disponible');
  });
});
