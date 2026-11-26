// Simula una pestaña del navegador en un hilo propio (contexto JavaScript separado).
// Carga los módulos reales de la app; Node elimina los tipos de los archivos .ts.
import { pathToFileURL } from 'node:url';
import { parentPort, workerData } from 'node:worker_threads';

// El SDK crea su BroadcastChannel solo si detecta un navegador
globalThis.window = globalThis;
globalThis.document = {};

const { createSupabaseClient, authStorageKey } = await import(pathToFileURL(workerData.clientModule).href);
const { MemorySessionStore } = await import(pathToFileURL(workerData.storeModule).href);

const PROJECT_REF = 'proyectoprueba';
const config = { url: 'https://proyectoprueba.supabase.co', publishableKey: 'sb_publishable_prueba123', projectRef: PROJECT_REF };

// Respuesta ficticia de GoTrue: sin red ni credenciales reales
const fakeFetch = async (input, init) => {
  const url = String(input);
  if (!url.includes('grant_type=password')) return new Response(null, { status: 204 });
  const email = JSON.parse(init.body).email;
  const now = Math.floor(Date.now() / 1000);
  const session = {
    access_token: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0YWIifQ.firma',
    token_type: 'bearer',
    expires_in: 3600,
    expires_at: now + 3600,
    refresh_token: 'refresh-de-prueba',
    user: { id: email.split('@')[0], email, aud: 'authenticated', role: 'authenticated', app_metadata: {}, user_metadata: {}, created_at: '2026-01-01T00:00:00Z' },
  };
  return new Response(JSON.stringify(session), { status: 200, headers: { 'Content-Type': 'application/json' } });
};

const store = new MemorySessionStore();
// 'shared' reproduce la clave fija anterior; 'isolated' usa la clave por pestaña
const storageKey = workerData.mode === 'shared' ? authStorageKey(PROJECT_REF) : authStorageKey(PROJECT_REF, crypto.randomUUID());
const client = createSupabaseClient(config, store, { storageKey, fetch: fakeFetch });

const received = [];
client.auth.onAuthStateChange((event, session) => {
  if (event === 'INITIAL_SESSION') return;
  received.push({ event, carriesTokens: Boolean(session?.access_token || session?.refresh_token) });
});

parentPort.on('message', async (message) => {
  if (message === 'signIn') {
    await client.auth.signInWithPassword({ email: `${workerData.user}@prueba.test`, password: 'x' });
    parentPort.postMessage({ type: 'signedIn' });
  } else if (message === 'report') {
    const { data } = await client.auth.getSession();
    parentPort.postMessage({ type: 'report', received, sessionUser: data.session?.user.id ?? null, stored: (await store.getItem(storageKey)) !== null });
  }
});

parentPort.postMessage({ type: 'ready' });
