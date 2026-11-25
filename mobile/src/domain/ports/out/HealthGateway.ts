import type { HealthCheck } from '../../model/ApiStatus';

// Puerto de salida: cualquier adaptador capaz de consultar la salud del backend
export interface HealthGateway {
  check(): Promise<HealthCheck>;
}
