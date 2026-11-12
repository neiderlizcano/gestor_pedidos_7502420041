import process from "node:process";

// std-env inspecciona variables al importar el SDK. Sustituir SOLO la vista del
// proceso de pruebas por datos sinteticos antes del import, sin leer el entorno.
Object.defineProperty(process, "env", { value: Object.freeze({ NODE_ENV: "test" }) });
const { createDeleteMyAccountHandler } = await import("./handler.ts");

// Claves, identidades y secretos son sinteticos. Tests sin permisos de red/entorno.
const A = "11111111-1111-1111-1111-111111111111";
const B = "22222222-2222-2222-2222-222222222222";
const URL_FUNCION = "https://project.invalid/functions/v1/delete-my-account";
const claves = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
const publica = await crypto.subtle.exportKey("jwk", claves.publicKey);
const env = {
  url: "https://project.invalid",
  publishableKeys: { default: "sb_publishable_sintetica" },
  secretKeys: { default: "sb_secret_sintetica" },
  jwks: { keys: [{ ...publica, kid: "efimera", alg: "ES256", use: "sig" }] },
};

function igual(actual: unknown, esperado: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(esperado)) throw new Error("Resultado local inesperado");
}

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function token(cambios: Record<string, unknown> = {}, otraFirma = false): Promise<string> {
  const encode = (obj: unknown) => base64url(new TextEncoder().encode(JSON.stringify(obj)));
  const datos = encode({ alg: "ES256", kid: "efimera" }) + "." + encode({
    sub: A, role: "authenticated", aud: "authenticated", iss: env.url + "/auth/v1",
    exp: Math.floor(Date.now() / 1000) + 300, ...cambios,
  });
  const llave = otraFirma
    ? (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])).privateKey
    : claves.privateKey;
  const firma = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, llave, new TextEncoder().encode(datos));
  return datos + "." + base64url(new Uint8Array(firma));
}

function request(jwt: string, opciones: RequestInit = {}, url = URL_FUNCION): Request {
  return new Request(url, { method: "DELETE", ...opciones, headers: {
    Authorization: "Bearer " + jwt, ...opciones.headers,
  } });
}

function remoto(opciones: { userId?: string; getStatus?: number; adminGetStatus?: number; deleteStatus?: number; fallo?: boolean } = {}) {
  const llamadas: { method: string; path: string; authorization: string | null; body: unknown }[] = [];
  const transporte: typeof fetch = async (input, init) => {
    const req = new Request(input, init);
    igual(new URL(req.url).origin, env.url);
    igual(init?.redirect, "error");
    const path = new URL(req.url).pathname;
    const body = req.body ? await req.json() : null;
    llamadas.push({ method: req.method, path, authorization: req.headers.get("Authorization"), body });
    if (opciones.fallo) throw new Error("fallo sintetico");
    if (path === "/auth/v1/user") {
      const status = opciones.getStatus ?? 200;
      return Response.json(status === 200 ? { id: opciones.userId ?? A, aud: "authenticated", role: "authenticated" }
        : { code: "unexpected_failure", msg: "detalle privado SQL" }, { status });
    }
    igual(path, "/auth/v1/admin/users/" + A);
    igual(req.headers.get("apikey"), "sb_secret_sintetica");
    // El SDK 2.119.0 consulta getUserById antes de DELETE para devolver el usuario.
    if (req.method === "GET") {
      const status = opciones.adminGetStatus ?? 200;
      return Response.json(status === 200 ? { id: A, aud: "authenticated", role: "authenticated" }
        : { code: "user_not_found", msg: "detalle privado" }, { status });
    }
    igual(req.method, "DELETE");
    igual(body, { should_soft_delete: false });
    const status = opciones.deleteStatus ?? 200;
    return Response.json(status === 200 ? {} : { code: "unexpected_failure", msg: "detalle privado SQL" }, { status });
  };
  return { llamadas, handler: createDeleteMyAccountHandler({ env, fetch: transporte }) };
}

Deno.test("DELETE confirmado: getUser verifica JWT y admin independiente elimina solo A sin devolver datos", async () => {
  const jwt = await token();
  const { handler, llamadas } = remoto();
  const response = await handler(request(jwt, { headers: { "X-Usuario-Id": B, "X-Email": "b@invalid" } }));
  igual(response.status, 204);
  igual(await response.text(), "");
  igual(response.headers.get("Cache-Control"), "no-store");
  igual(llamadas.length, 3);
  igual(llamadas[0].authorization, "Bearer " + jwt);
  igual(llamadas[1].authorization, "Bearer sb_secret_sintetica");
  igual(llamadas[2].authorization, "Bearer sb_secret_sintetica");
});

for (const metodo of ["GET", "POST", "PUT", "OPTIONS"]) {
  Deno.test("rechaza metodo " + metodo, async () => {
    const { handler, llamadas } = remoto();
    igual((await handler(new Request(URL_FUNCION, { method: metodo }))).status, 405);
    igual(llamadas.length, 0);
  });
}

for (const authorization of ["", "Bearer sb_publishable_sintetica", "Bearer no.jwt.valido"]) {
  Deno.test("credencial no valida " + authorization.length, async () => {
    const { handler, llamadas } = remoto();
    igual((await handler(new Request(URL_FUNCION, { method: "DELETE", headers: { Authorization: authorization } }))).status, 401);
    igual(llamadas.length, 0);
  });
}

for (const [nombre, cambios] of Object.entries({
  expirado: { exp: 1 }, sinExp: { exp: null }, rol: { role: "service_role" },
  emisor: { iss: "https://otro.invalid/auth/v1" }, audiencia: { aud: "otro" },
  subInvalido: { sub: "otro" }, subNulo: { sub: null },
})) {
  Deno.test("JWT no autorizado " + nombre, async () => {
    const { handler, llamadas } = remoto();
    igual((await handler(request(await token(cambios)))).status, 401);
    igual(llamadas.length, 0);
  });
}

Deno.test("firma ES256 ajena no autoriza", async () => {
  const { handler, llamadas } = remoto();
  igual((await handler(request(await token({}, true)))).status, 401);
  igual(llamadas.length, 0);
});

for (const cuerpo of ["{}", " ", JSON.stringify({ id: B })]) {
  Deno.test("rechaza cuerpo de longitud " + cuerpo.length, async () => {
    const { handler, llamadas } = remoto();
    igual((await handler(request(await token(), { body: cuerpo }))).status, 400);
    igual(llamadas.length, 0);
  });
}

for (const sufijo of ["?id=" + B, "?email=otro", "?", "/" + B]) {
  Deno.test("rechaza destino arbitrario " + sufijo.slice(0, 8), async () => {
    const { handler, llamadas } = remoto();
    igual((await handler(request(await token(), {}, URL_FUNCION + sufijo))).status, 400);
    igual(llamadas.length, 0);
  });
}

Deno.test("getUser discordante impide llamar al administrador", async () => {
  const { handler, llamadas } = remoto({ userId: B });
  igual((await handler(request(await token()))).status, 401);
  igual(llamadas.length, 1);
});

for (const [getStatus, estado] of [[401, 401], [404, 401], [500, 502], [503, 503]]) {
  Deno.test("getUser fallo " + getStatus, async () => {
    const { handler, llamadas } = remoto({ getStatus });
    const response = await handler(request(await token()));
    igual(response.status, estado);
    igual((await response.text()).includes("SQL"), false);
    igual(llamadas.length, 1);
  });
}

for (const [deleteStatus, estado] of [[202, 502], [401, 502], [404, 401], [409, 502], [500, 502], [503, 503]]) {
  Deno.test("admin fallo " + deleteStatus + " nunca es exito ni conflicto inferido", async () => {
    const { handler, llamadas } = remoto({ deleteStatus });
    const response = await handler(request(await token()));
    igual(response.status, estado);
    igual((await response.text()).includes("SQL"), false);
    igual(llamadas.length, 3);
  });
}

Deno.test("transporte falla sin reintentos ni eliminacion", async () => {
  const { handler, llamadas } = remoto({ fallo: true });
  igual((await handler(request(await token()))).status, 503);
  igual(llamadas.length, 1);
});

Deno.test("sin secreto administrativo falla cerrado", async () => {
  const handler = createDeleteMyAccountHandler({ env: { ...env, secretKeys: {} }, fetch: () => {
    throw new Error("No debe llamar a Auth");
  } });
  igual((await handler(request(await token()))).status, 503);
});

Deno.test("getUserById del SDK rechaza usuario inexistente sin DELETE", async () => {
  const { handler, llamadas } = remoto({ adminGetStatus: 404 });
  igual((await handler(request(await token()))).status, 401);
  igual(llamadas.length, 2);
  igual(llamadas.some((x) => x.method === "DELETE"), false);
});

Deno.test("presupuesto agotado es 504 sin reintento ni DELETE", async () => {
  let llamadas = 0;
  const handler = createDeleteMyAccountHandler({ env, fetch: (_input, init) => {
    llamadas++;
    return new Promise((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => reject(new Error("cancelado")), { once: true });
    });
  } });
  igual((await handler(request(await token()))).status, 504);
  igual(llamadas, 1);
});

function flujo(chunks: Uint8Array[] = []): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controlador) {
      for (const chunk of chunks) controlador.enqueue(chunk);
      controlador.close();
    },
  });
}

for (const [nombre, cuerpo, headers] of [
  ["null sin longitud", () => null, {}],
  ["null longitud cero", () => null, { "Content-Length": "0" }],
  ["flujo vacio sin longitud", () => flujo(), {}],
  ["flujo vacio longitud cero", () => flujo(), { "Content-Length": "0" }],
  ["chunks vacios", () => flujo([new Uint8Array(), new Uint8Array()]), {}],
  ["chunked vacio", () => flujo(), { "Transfer-Encoding": "chunked" }],
  ["chunked mayusculas", () => flujo([new Uint8Array()]), { "Transfer-Encoding": "Chunked" }],
] as [string, () => ReadableStream<Uint8Array> | null, Record<string, string>][]) {
  Deno.test("acepta cuerpo realmente vacio: " + nombre, async () => {
    const { handler, llamadas } = remoto();
    const req = request(await token(), { body: cuerpo(), headers: { ...headers, "X-Usuario-Id": B } });
    const response = await handler(req);
    igual(response.status, 204);
    igual(llamadas.length, 3);
    igual(llamadas.filter((x) => x.method === "DELETE").map((x) => x.path), ["/auth/v1/admin/users/" + A]);
    igual(req.body?.locked ?? false, false);
  });
}

async function verificarRechazo(req: Request, codigo: string) {
  const { handler, llamadas } = remoto();
  const avisos: unknown[][] = [];
  const original = console.warn;
  console.warn = (...datos: unknown[]) => { avisos.push(datos); };
  try {
    const response = await handler(req);
    igual(response.status, 400);
    igual(await response.json(), { code: "INVALID_REQUEST" });
    igual(avisos, [[codigo]]);
    igual(llamadas.length, 0);
    igual(req.body?.locked ?? false, false);
  } finally {
    console.warn = original;
  }
}

for (const [nombre, headers] of [
  ["longitud y chunked", { "Content-Length": "0", "Transfer-Encoding": "chunked" }],
  ["longitud positiva", { "Content-Length": "1" }],
  ["longitud negativa", { "Content-Length": "-1" }],
  ["longitud no decimal", { "Content-Length": "0x0" }],
  ["longitud vacia", { "Content-Length": "" }],
  ["longitud duplicada", { "Content-Length": "0, 0" }],
  ["codificacion no soportada", { "Transfer-Encoding": "gzip, chunked" }],
  ["chunked duplicado", { "Transfer-Encoding": "chunked, chunked" }],
  ["transferencia vacia", { "Transfer-Encoding": "" }],
] as [string, Record<string, string>][]) {
  Deno.test("rechaza framing: " + nombre, async () => {
    await verificarRechazo(request(await token(), { body: flujo(), headers }), "FRAMING_REJECTED");
  });
}

for (const [nombre, contenido, headers] of [
  ["un byte", new Uint8Array([0]), {}],
  ["espacio", new TextEncoder().encode(" "), {}],
  ["JSON con UUID ajeno", new TextEncoder().encode(JSON.stringify({ id: B })), {}],
  ["longitud cero no oculta bytes", new Uint8Array([1]), { "Content-Length": "0" }],
  ["chunked con contenido", new Uint8Array([1]), { "Transfer-Encoding": "chunked" }],
] as [string, Uint8Array, Record<string, string>][]) {
  Deno.test("rechaza contenido tras chunks vacios: " + nombre, async () => {
    let cancelado = false;
    const body = new ReadableStream<Uint8Array>({
      start(c) { c.enqueue(new Uint8Array()); c.enqueue(contenido); },
      cancel() { cancelado = true; },
    });
    await verificarRechazo(request(await token(), { body, headers }), "BODY_NONEMPTY");
    igual(cancelado, true); // Rechaza sin esperar EOF.
  });
}

Deno.test("flujo bloqueado agota plazo y libera lector aunque cancel no termine", async () => {
  let cancelado = false;
  const body = new ReadableStream<Uint8Array>({
    cancel() { cancelado = true; return new Promise<void>(() => {}); },
  });
  const inicio = performance.now();
  await verificarRechazo(request(await token(), { body }), "BODY_READ_TIMEOUT");
  igual(cancelado, true);
  igual(performance.now() - inicio < 5000, true);
});

Deno.test("chunks vacios continuos tambien tienen plazo", async () => {
  let cancelado = false;
  const body = new ReadableStream<Uint8Array>({
    pull(c) { c.enqueue(new Uint8Array()); },
    cancel() { cancelado = true; },
  });
  await verificarRechazo(request(await token(), { body }), "BODY_READ_TIMEOUT");
  igual(cancelado, true);
});

for (const previo of [true, false]) {
  Deno.test("rechaza flujo abortado " + (previo ? "antes" : "durante lectura"), async () => {
    const controlador = new AbortController();
    let cancelado = false;
    const body = new ReadableStream<Uint8Array>({
      pull() { if (!previo) controlador.abort(); },
      cancel() { cancelado = true; },
    });
    if (previo) controlador.abort();
    await verificarRechazo(request(await token(), { body, signal: controlador.signal }), "BODY_READ_ERROR");
    igual(cancelado, true);
  });
}

Deno.test("error de lectura no revela causa ni llama a Auth", async () => {
  const body = new ReadableStream<Uint8Array>({
    pull(c) { c.error(new Error("detalle sintetico que no debe registrarse")); },
  });
  await verificarRechazo(request(await token(), { body }), "BODY_READ_ERROR");
});

for (const [sufijo, codigo] of [["/" + B, "PATH_REJECTED"], ["?id=" + B, "QUERY_REJECTED"], ["?", "QUERY_REJECTED"]]) {
  Deno.test("diagnostico constante sin URL: " + codigo + sufijo.length, async () => {
    await verificarRechazo(request(await token(), { body: flujo() }, URL_FUNCION + sufijo), codigo);
  });
}

Deno.test("ruta corta tambien acepta flujo vacio", async () => {
  const { handler, llamadas } = remoto();
  igual((await handler(request(await token(), { body: flujo() }, env.url + "/delete-my-account"))).status, 204);
  igual(llamadas.filter((x) => x.method === "DELETE").map((x) => x.path), ["/auth/v1/admin/users/" + A]);
});

Deno.test("flujo vacio no permite JWT de firma ajena", async () => {
  const { handler, llamadas } = remoto();
  igual((await handler(request(await token({}, true), { body: flujo() }))).status, 401);
  igual(llamadas.length, 0);
});

Deno.test("flujo vacio no permite identidad getUser discordante", async () => {
  const { handler, llamadas } = remoto({ userId: B });
  igual((await handler(request(await token(), { body: flujo() }))).status, 401);
  igual(llamadas.length, 1);
  igual(llamadas.some((x) => x.method === "DELETE"), false);
});
