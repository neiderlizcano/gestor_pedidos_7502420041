import { describe, expect, it } from 'vitest';
import { HealthHttpAdapter } from '../../src/adapters/http/HealthHttpAdapter';
import { HttpClient, type FetchFn } from '../../src/adapters/http/HttpClient';

// Fetch falso que responde con un código y un cuerpo de texto fijos
const respond = (status: number, body = ''): FetchFn => async () => new Response(body || null, { status });
const adapterWith = (fetchFn: FetchFn, timeoutMs = 1000) =>
  new HealthHttpAdapter(new HttpClient('https://api.test', fetchFn, timeoutMs));

describe('HealthHttpAdapter', () => {
  it('consulta /actuator/health sobre la base configurada', async () => {
    let requested = '';
    await adapterWith(async (url) => {
      requested = url;
      return new Response('{"status":"UP"}', { status: 200 });
    }).check();
    expect(requested).toBe('https://api.test/actuator/health');
  });

  it.each([
    ['UP con 200', 200, '{"status":"UP"}', 'UP'],
    ['DOWN con 200', 200, '{"status":"DOWN"}', 'DOWN'],
    ['DOWN de Actuator con 503', 503, '{"status":"DOWN"}', 'DOWN'],
    ['OUT_OF_SERVICE con 503', 503, '{"status":"OUT_OF_SERVICE"}', 'DOWN'],
    ['503 sin cuerpo de salud', 503, '<html>Service Unavailable</html>', 'UNAVAILABLE'],
    ['503 vacío', 503, '', 'UNAVAILABLE'],
    ['404', 404, '{"error":"Not Found"}', 'NOT_FOUND'],
    ['500', 500, '', 'HTTP_ERROR'],
    ['401', 401, '', 'HTTP_ERROR'],
    ['200 con HTML', 200, '<!doctype html><html></html>', 'INVALID_RESPONSE'],
    ['200 vacío', 200, '', 'INVALID_RESPONSE'],
    ['200 con estado desconocido', 200, '{"status":"UNKNOWN"}', 'INVALID_RESPONSE'],
    ['200 con status no textual', 200, '{"status":1}', 'INVALID_RESPONSE'],
    ['200 con arreglo JSON', 200, '[]', 'INVALID_RESPONSE'],
  ])('%s → %s', async (_name, status, body, expected) => {
    const result = await adapterWith(respond(status, body)).check();
    expect(result).toEqual({ state: expected, httpStatus: status });
  });

  it('reporta fallo de conexión cuando fetch rechaza', async () => {
    const result = await adapterWith(async () => {
      throw new TypeError('Failed to fetch');
    }).check();
    expect(result).toEqual({ state: 'CONNECTION_ERROR', httpStatus: null });
  });

  it('reporta tiempo agotado cuando la petición se aborta', async () => {
    const hanging: FetchFn = (_url, init) =>
      new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      });
    const result = await adapterWith(hanging, 5).check();
    expect(result).toEqual({ state: 'TIMEOUT', httpStatus: null });
  });

  it('reporta tiempo agotado si llegan las cabeceras 200 pero el cuerpo se demora', async () => {
    // Envía una parte del JSON y nunca cierra el flujo
    const slowBody: FetchFn = async () =>
      new Response(
        new ReadableStream<Uint8Array>({
          start(controller) {
            controller.enqueue(new TextEncoder().encode('{"status":'));
          },
        }),
        { status: 200 },
      );
    const result = await adapterWith(slowBody, 20).check();
    expect(result).toEqual({ state: 'TIMEOUT', httpStatus: null });
  });

  it('reporta fallo de conexión si el cuerpo se corta antes del plazo', async () => {
    const brokenBody: FetchFn = async () =>
      new Response(
        new ReadableStream<Uint8Array>({
          start(controller) {
            controller.error(new TypeError('network error'));
          },
        }),
        { status: 200 },
      );
    const result = await adapterWith(brokenBody).check();
    expect(result).toEqual({ state: 'CONNECTION_ERROR', httpStatus: null });
  });
});
