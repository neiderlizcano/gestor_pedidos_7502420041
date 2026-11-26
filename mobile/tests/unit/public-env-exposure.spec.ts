import { fileURLToPath } from 'node:url';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { build, createServer, type ViteDevServer } from 'vite';
import { buildPublicEnv } from '../../src/bootstrap/public-env';

// Valores sintéticos: no son credenciales reales
const UNAUTHORIZED_NAME = 'VITE_NO_AUTORIZADA_SINTETICA';
const UNAUTHORIZED_VALUE = 'valor-sintetico-no-autorizado-7f3a9c';
const SYNTHETIC_ENV = {
  [UNAUTHORIZED_NAME]: UNAUTHORIZED_VALUE,
  VITE_SUPABASE_URL: 'https://sintetico.supabase.co',
  VITE_SUPABASE_PUBLISHABLE_KEY: 'sb_publishable_sintetico123',
};

const root = fileURLToPath(new URL('../..', import.meta.url));
const configFile = fileURLToPath(new URL('../../vite.config.ts', import.meta.url));
const saved: Record<string, string | undefined> = {};

beforeAll(() => {
  for (const [name, value] of Object.entries(SYNTHETIC_ENV)) {
    saved[name] = process.env[name];
    process.env[name] = value;
  }
});

afterAll(() => {
  for (const [name, value] of Object.entries(saved)) {
    if (value === undefined) delete process.env[name];
    else process.env[name] = value;
  }
});

// Captura lo que el build escribe en consola para comprobar que no filtra valores
function captureConsole() {
  const lines: string[] = [];
  const spies = (['log', 'info', 'warn', 'error'] as const).map((method) =>
    vi.spyOn(console, method).mockImplementation((...args: unknown[]) => {
      lines.push(args.map(String).join(' '));
    }),
  );
  return { lines, restore: () => spies.forEach((spy) => spy.mockRestore()) };
}

describe('buildPublicEnv', () => {
  it('publica solo la lista cerrada y devuelve únicamente los nombres ignorados', () => {
    const result = buildPublicEnv({ ...SYNTHETIC_ENV, VITE_API_BASE_URL: '', PATH: 'x' });
    expect(result.publicEnv).toEqual({
      VITE_SUPABASE_URL: SYNTHETIC_ENV.VITE_SUPABASE_URL,
      VITE_SUPABASE_PUBLISHABLE_KEY: SYNTHETIC_ENV.VITE_SUPABASE_PUBLISHABLE_KEY,
    });
    expect(result.ignoredNames).toEqual([UNAUTHORIZED_NAME]);
    expect(JSON.stringify(result)).not.toContain(UNAUTHORIZED_VALUE);
  });

  it('sigue rechazando claves administrativas y configuraciones incompletas', () => {
    expect(() => buildPublicEnv({ VITE_SUPABASE_URL: 'https://x.supabase.co' })).toThrow();
    expect(() => buildPublicEnv({ VITE_OTRA: 'sb_secret_sintetico123' })).toThrow();
  });
});

describe('exposición de variables en Vite (valores sintéticos)', () => {
  let server: ViteDevServer | null = null;

  afterAll(async () => {
    await server?.close();
  });

  it('el servidor de desarrollo no sirve la variable no autorizada', async () => {
    const output = captureConsole();
    try {
      server = await createServer({
        root,
        configFile,
        mode: 'development',
        logLevel: 'silent',
        server: { port: 0, strictPort: false, hmr: false },
      });
      await server.listen();
    } finally {
      output.restore();
    }
    const base = server.resolvedUrls!.local[0];
    const paths = [
      '/',
      '/@vite/env',
      '/@vite/client',
      '/src/main.ts',
      '/src/bootstrap/public-env.ts',
      '/src/bootstrap/auth-container.ts',
      '/src/bootstrap/container.ts',
    ];
    const bodies = await Promise.all(paths.map(async (path) => (await fetch(new URL(path, base))).text()));
    const served = bodies.join('\n');

    expect(served).not.toContain(UNAUTHORIZED_VALUE);
    expect(served).not.toContain(UNAUTHORIZED_NAME);
    // Las variables autorizadas sí llegan, por el canal explícito
    expect(served).toContain(SYNTHETIC_ENV.VITE_SUPABASE_URL);
    // El aviso de la consola nombra la variable pero no muestra su valor
    expect(output.lines.join('\n')).not.toContain(UNAUTHORIZED_VALUE);
    expect(output.lines.join('\n')).not.toContain(SYNTHETIC_ENV.VITE_SUPABASE_PUBLISHABLE_KEY);
  }, 60_000);

  it('el JavaScript generado por el build no contiene la variable no autorizada', async () => {
    const output = captureConsole();
    let result;
    try {
      result = await build({ root, configFile, mode: 'production', logLevel: 'silent', build: { write: false } });
    } finally {
      output.restore();
    }
    const outputs = (Array.isArray(result) ? result : [result]) as { output: { type: string; code?: string; source?: string | Uint8Array }[] }[];
    const generated = outputs
      .flatMap((bundle) => bundle.output)
      .map((file) => (file.type === 'chunk' ? file.code ?? '' : typeof file.source === 'string' ? file.source : Buffer.from(file.source ?? []).toString('utf8')))
      .join('\n');

    expect(generated).not.toContain(UNAUTHORIZED_VALUE);
    expect(generated).not.toContain(UNAUTHORIZED_NAME);
    expect(generated).toContain(SYNTHETIC_ENV.VITE_SUPABASE_PUBLISHABLE_KEY);
    expect(output.lines.join('\n')).not.toContain(UNAUTHORIZED_VALUE);
  }, 120_000);
});

describe('URL públicas con datos no permitidos (valores sintéticos)', () => {
  const SECRET_PART = 'contrasena-sintetica-91b2';

  // Aplica una variable solo durante la prueba
  async function withEnv<T>(name: string, value: string, run: () => Promise<T>): Promise<T> {
    const previous = process.env[name];
    process.env[name] = value;
    try {
      return await run();
    } finally {
      if (previous === undefined) delete process.env[name];
      else process.env[name] = previous;
    }
  }

  async function expectRejectedWithoutLeak(start: () => Promise<unknown>) {
    const output = captureConsole();
    let failure: unknown = null;
    try {
      const started = await start();
      // Si llegara a arrancar, se cierra para no dejar el servidor abierto
      await (started as { close?: () => Promise<void> } | null)?.close?.();
    } catch (error) {
      failure = error;
    } finally {
      output.restore();
    }
    expect(failure).toBeInstanceOf(Error);
    const text = `${(failure as Error).message}\n${(failure as Error).stack}\n${output.lines.join('\n')}`;
    expect(text).not.toContain(SECRET_PART);
  }

  it.each(['VITE_API_BASE_URL', 'VITE_NATIVE_API_BASE_URL', 'VITE_SUPABASE_URL'])(
    '%s con contraseña detiene el servidor de desarrollo antes de servir',
    async (name) => {
      await withEnv(name, `https://usuario:${SECRET_PART}@api.test`, () =>
        expectRejectedWithoutLeak(() =>
          createServer({ root, configFile, mode: 'development', logLevel: 'silent', server: { port: 0, strictPort: false, hmr: false } }),
        ),
      );
    },
    60_000,
  );

  it.each(['VITE_API_BASE_URL', 'VITE_NATIVE_API_BASE_URL'])(
    '%s con contraseña detiene el build antes de compilar',
    async (name) => {
      await withEnv(name, `https://usuario:${SECRET_PART}@api.test`, () =>
        expectRejectedWithoutLeak(() =>
          build({ root, configFile, mode: 'production', logLevel: 'silent', build: { write: false } }),
        ),
      );
    },
    60_000,
  );

  it.each([
    ['ruta', 'https://api.test/base'],
    ['parámetros', `https://api.test/?token=${SECRET_PART}`],
    ['fragmento', `https://api.test/#${SECRET_PART}`],
  ])('VITE_NATIVE_API_BASE_URL con %s detiene el build', async (_reason, value) => {
    await withEnv('VITE_NATIVE_API_BASE_URL', value, () =>
      expectRejectedWithoutLeak(() => build({ root, configFile, mode: 'production', logLevel: 'silent', build: { write: false } })),
    );
  }, 60_000);

  it('publica solo el origen normalizado de las bases válidas', () => {
    const { publicEnv } = buildPublicEnv({ VITE_API_BASE_URL: 'https://api.test/', VITE_NATIVE_API_BASE_URL: 'https://nativo.test' });
    expect(publicEnv).toEqual({ VITE_API_BASE_URL: 'https://api.test', VITE_NATIVE_API_BASE_URL: 'https://nativo.test' });
  });
});
