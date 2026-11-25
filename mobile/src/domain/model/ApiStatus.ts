// Resultado de consultar la salud del backend
export type ServiceState =
  | 'UP' // el backend responde y está operativo
  | 'DOWN' // el backend responde, pero informa que no está operativo
  | 'UNAVAILABLE' // HTTP 503 sin cuerpo de salud (arranque, mantenimiento o pasarela)
  | 'NOT_FOUND' // HTTP 404: la URL no expone /actuator/health
  | 'HTTP_ERROR' // cualquier otro código HTTP inesperado
  | 'INVALID_RESPONSE' // el cuerpo no es un JSON de salud reconocible
  | 'TIMEOUT'
  | 'CONNECTION_ERROR'
  | 'MISCONFIGURED'; // la URL configurada no es válida

export interface HealthCheck {
  readonly state: ServiceState;
  readonly httpStatus: number | null;
}

export interface ApiStatus extends HealthCheck {
  readonly checkedAt: Date;
  // Solo se mide cuando la consulta fue exitosa
  readonly latencyMs: number | null;
}

export const isServiceAvailable = (status: Pick<ApiStatus, 'state'>): boolean => status.state === 'UP';
