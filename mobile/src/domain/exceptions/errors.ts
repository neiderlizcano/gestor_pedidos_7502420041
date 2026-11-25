// La red no respondió: sin conexión, DNS, CORS o tiempo agotado
export class NetworkError extends Error {
  constructor(readonly reason: 'connection' | 'timeout') {
    super(reason === 'timeout' ? 'Tiempo de espera agotado.' : 'No se pudo conectar con el servidor.');
    this.name = 'NetworkError';
  }
}

// Configuración pública ausente o mal formada
export class ConfigurationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigurationError';
  }
}
