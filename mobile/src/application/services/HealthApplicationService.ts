import type { ApiStatus, HealthCheck } from '../../domain/model/ApiStatus';
import type { HealthGateway } from '../../domain/ports/out/HealthGateway';
import type { UseCases } from '../ports/in/use-cases';

export class HealthApplicationService implements UseCases {
  constructor(
    private readonly gateway: HealthGateway,
    private readonly now: () => number = () => Date.now(),
  ) {}

  async checkApiHealth(): Promise<ApiStatus> {
    const start = this.now();
    // Un error inesperado del adaptador no debe romper la pantalla
    const result: HealthCheck = await this.gateway
      .check()
      .catch(() => ({ state: 'CONNECTION_ERROR', httpStatus: null }) as const);
    const end = this.now();
    return {
      ...result,
      checkedAt: new Date(end),
      latencyMs: result.state === 'UP' ? end - start : null,
    };
  }
}
