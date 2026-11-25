import type { HealthCheck } from '../../domain/model/ApiStatus';
import type { HealthGateway } from '../../domain/ports/out/HealthGateway';

// Se usa cuando la URL configurada es inválida: la app abre y explica el problema
export class MisconfiguredHealthGateway implements HealthGateway {
  async check(): Promise<HealthCheck> {
    return { state: 'MISCONFIGURED', httpStatus: null };
  }
}
