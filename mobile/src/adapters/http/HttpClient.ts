import { NetworkError } from '../../domain/exceptions/errors';

export interface HttpResponse {
  readonly status: number;
  // undefined cuando el cuerpo está vacío o no es JSON válido
  readonly body: unknown;
}

export type FetchFn = (input: string, init?: RequestInit) => Promise<Response>;

export class HttpClient {
  constructor(
    private readonly baseUrl: string,
    private readonly fetchFn: FetchFn = (input, init) => globalThis.fetch(input, init),
    private readonly timeoutMs = 30000,
  ) {}

  // Devuelve cualquier código HTTP; lanza NetworkError si la respuesta no llega completa
  async get(path: string): Promise<HttpResponse> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    // El plazo cubre cabeceras y cuerpo: se libera solo al terminar la lectura
    const aborted = new Promise<never>((_resolve, reject) => {
      controller.signal.addEventListener('abort', () => reject(new NetworkError('timeout')), { once: true });
    });
    aborted.catch(() => undefined);
    const failure = () => new NetworkError(controller.signal.aborted ? 'timeout' : 'connection');

    try {
      const response = await Promise.race([
        this.fetchFn(`${this.baseUrl}${path}`, {
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        }),
        aborted,
      ]).catch((error: unknown) => {
        throw error instanceof NetworkError ? error : failure();
      });
      // Si la conexión se corta o vence el plazo durante la lectura, no es una respuesta inválida
      const text = await Promise.race([response.text(), aborted]).catch((error: unknown) => {
        throw error instanceof NetworkError ? error : failure();
      });
      return { status: response.status, body: parseJson(text) };
    } finally {
      clearTimeout(timeout);
    }
  }
}

function parseJson(text: string): unknown {
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}
