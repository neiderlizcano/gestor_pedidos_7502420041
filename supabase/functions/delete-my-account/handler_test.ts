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
