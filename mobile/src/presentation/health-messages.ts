import type { ApiStatus, ServiceState } from '../domain/model/ApiStatus';

export type Tone = 'success' | 'warning' | 'danger';

export interface HealthMessage {
  readonly title: string;
  readonly detail: string;
  readonly tone: Tone;
}

const MESSAGES: Record<ServiceState, HealthMessage> = {
  UP: { title: 'Disponible', detail: 'El servidor responde correctamente.', tone: 'success' },
  DOWN: { title: 'Servicio caído', detail: 'El servidor respondió, pero informa que no está operativo.', tone: 'danger' },
  UNAVAILABLE: {
    title: 'Servicio no disponible',
    detail: 'El servidor está iniciando o en mantenimiento. Intenta de nuevo en unos segundos.',
    tone: 'warning',
  },
  NOT_FOUND: {
    title: 'Ruta no encontrada',
    detail: 'La URL configurada no ofrece el estado del servicio. Revisa la dirección del backend.',
    tone: 'danger',
  },
  HTTP_ERROR: { title: 'Error del servidor', detail: 'El servidor devolvió una respuesta inesperada.', tone: 'danger' },
  INVALID_RESPONSE: {
    title: 'Respuesta no válida',
    detail: 'El servidor respondió con un formato que la aplicación no reconoce.',
    tone: 'danger',
  },
  TIMEOUT: {
    title: 'Tiempo de espera agotado',
    detail: 'El servidor tardó demasiado en responder. Puede estar despertando; intenta de nuevo.',
    tone: 'warning',
  },
  CONNECTION_ERROR: {
    title: 'Sin conexión',
    detail: 'No se pudo contactar al servidor. Verifica tu conexión a internet.',
    tone: 'danger',
  },
  MISCONFIGURED: {
    title: 'Configuración inválida',
    detail: 'La dirección del backend debe ser una URL HTTPS absoluta.',
    tone: 'danger',
  },
};

export function describeHealth(status: Pick<ApiStatus, 'state' | 'httpStatus'>): HealthMessage {
  const base = MESSAGES[status.state];
  // El código HTTP ayuda a diagnosticar sin ocultar el mensaje principal
  return status.httpStatus !== null && status.state !== 'UP'
    ? { ...base, title: `${base.title} (HTTP ${status.httpStatus})` }
    : base;
}
