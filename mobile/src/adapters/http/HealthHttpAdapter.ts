import { NetworkError } from '../../domain/exceptions/errors';
import type { HealthCheck } from '../../domain/model/ApiStatus';
import type { HealthGateway } from '../../domain/ports/out/HealthGateway';
import type { HttpClient } from './HttpClient';

// Estados que Spring Actuator puede publicar en /actuator/health
const DOWN_STATUSES = new Set(['DOWN', 'OUT_OF_SERVICE']);

function readActuatorStatus(body: unknown): string | null {
  if (typeof body !== 'object' || body === null) return null;
  const status = (body as { status?: unknown }).status;
  return typeof status === 'string' ? status : null;
}

export class HealthHttpAdapter implements HealthGateway {
  constructor(private readonly http: HttpClient) {}

  async check(): Promise<HealthCheck> {
    let response;
    try {
      response = await this.http.get('/actuator/health');
    } catch (error) {
      const state = error instanceof NetworkError && error.reason === 'timeout' ? 'TIMEOUT' : 'CONNECTION_ERROR';
      return { state, httpStatus: null };
    }

    const { status: httpStatus, body } = response;
    const actuator = readActuatorStatus(body);

    if (httpStatus === 404) return { state: 'NOT_FOUND', httpStatus };
    // Actuator responde 503 con {"status":"DOWN"}; sin ese cuerpo es la pasarela o un arranque
    if (httpStatus === 503) {
      return { state: actuator && DOWN_STATUSES.has(actuator) ? 'DOWN' : 'UNAVAILABLE', httpStatus };
    }
    if (httpStatus < 200 || httpStatus >= 300) return { state: 'HTTP_ERROR', httpStatus };

    if (actuator === 'UP') return { state: 'UP', httpStatus };
    if (actuator && DOWN_STATUSES.has(actuator)) return { state: 'DOWN', httpStatus };
    return { state: 'INVALID_RESPONSE', httpStatus };
  }
}
