import { fileURLToPath } from 'node:url';
import { Worker } from 'node:worker_threads';
import { afterEach, describe, expect, it } from 'vitest';

// Dos "pestañas" en hilos distintos: cada una tiene su propio contexto JavaScript,
// su propio cliente de Supabase y su propia memoria. BroadcastChannel sí cruza entre hilos.
const workerUrl = new URL('../support/tab-worker.mjs', import.meta.url);
const modules = {
  clientModule: fileURLToPath(new URL('../../src/adapters/supabase/createSupabaseClient.ts', import.meta.url)),
  storeModule: fileURLToPath(new URL('../../src/adapters/storage/MemorySessionStore.ts', import.meta.url)),
};

interface Report {
  received: { event: string; carriesTokens: boolean }[];
  sessionUser: string | null;
  stored: boolean;
}

const workers: Worker[] = [];

function openTab(mode: 'shared' | 'isolated', user: string) {
  const worker = new Worker(workerUrl, { workerData: { ...modules, mode, user } });
  workers.push(worker);
  const next = (type: string) =>
    new Promise<Record<string, unknown>>((resolve, reject) => {
      const onMessage = (message: Record<string, unknown>) => {
        if (message.type !== type) return;
        worker.off('message', onMessage);
        resolve(message);
      };
      worker.on('message', onMessage);
      worker.once('error', reject);
    });
  return {
    ready: next('ready'),
    async signIn() {
      const done = next('signedIn');
      worker.postMessage('signIn');
      await done;
    },
    async report(): Promise<Report> {
      const done = next('report');
      worker.postMessage('report');
      return (await done) as unknown as Report;
    },
  };
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 300));

afterEach(async () => {
  await Promise.all(workers.splice(0).map((worker) => worker.terminate()));
});

describe('aislamiento entre pestañas (SDK real, dos contextos)', () => {
  it('con una clave compartida, la otra pestaña recibe la sesión con tokens (problema original)', async () => {
    const tabA = openTab('shared', 'a');
    const tabB = openTab('shared', 'b');
    await Promise.all([tabA.ready, tabB.ready]);

    await tabA.signIn();
    await settle();
    const report = await tabB.report();
    expect(report.received).toContainEqual({ event: 'SIGNED_IN', carriesTokens: true });
  });

  it('con la clave por pestaña, la otra pestaña no recibe eventos ni tokens', async () => {
    const tabA = openTab('isolated', 'a');
    const tabB = openTab('isolated', 'b');
    await Promise.all([tabA.ready, tabB.ready]);

    await tabA.signIn();
    await settle();
    const reportB = await tabB.report();
    expect(reportB).toEqual({ type: 'report', received: [], sessionUser: null, stored: false });

    const reportA = await tabA.report();
    expect(reportA.sessionUser).toBe('a');
    expect(reportA.stored).toBe(true);
  });
});
