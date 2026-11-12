import { withSupabase, type SupabaseEnv } from "@supabase/server";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const UUID_CERO = "00000000-0000-0000-0000-000000000000";

function respuesta(status: number): Response {
  const headers = new Headers({ "Cache-Control": "no-store" });
  if (status === 204) return new Response(null, { status, headers });
  if (status === 401) headers.set("WWW-Authenticate", "Bearer");
  if (status === 405) headers.set("Allow", "DELETE");
  const code = status === 401 ? "AUTH_REQUIRED"
    : status === 400 ? "INVALID_REQUEST"
    : status === 405 ? "METHOD_NOT_ALLOWED"
    : status === 504 ? "UPSTREAM_TIMEOUT"
    : status === 503 ? "SERVICE_UNAVAILABLE"
    : "UPSTREAM_ERROR";
  return Response.json({ code }, { status, headers });
}

// Inyeccion solo para pruebas locales; no hay opciones controladas por el solicitante.
export function createDeleteMyAccountHandler(options: {
  env?: SupabaseEnv;
  fetch?: typeof fetch;
} = {}): (req: Request) => Promise<Response> {
  return async (req) => {
    if (req.method !== "DELETE") return respuesta(405);
    const bearer = /^Bearer ([A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)$/i
      .exec(req.headers.get("Authorization") ?? "");
    if (!bearer) return respuesta(401);
    // Rechazo conservador de streams de cuerpo, incluso de longitud desconocida.
    const path = new URL(req.url).pathname;
    if (!["/delete-my-account", "/functions/v1/delete-my-account"].includes(path)
      || req.url.includes("?") || req.body !== null
      || req.headers.has("Transfer-Encoding")
      || (req.headers.has("Content-Length") && req.headers.get("Content-Length") !== "0")) {
      return respuesta(400);
    }

    let agotado = false;
    let confirmadoPorAuth = false;
    const controlador = new AbortController();
    const limite = controlador.signal;
    const reloj = setTimeout(() => controlador.abort(), 6000); // Presupuesto compartido.
    const transporte: typeof fetch = async (input, init) => {
      try {
        const resultado = await (options.fetch ?? fetch)(input, {
          ...init,
          redirect: "error",
          signal: AbortSignal.any([limite, req.signal, ...(init?.signal ? [init.signal] : [])]),
        });
        const method = init?.method ?? (input instanceof Request ? input.method : "GET");
        if (method === "DELETE") confirmadoPorAuth = resultado.status === 200;
        return resultado;
      } catch {
        agotado = limite.aborted;
        // El SDK nunca recibe excepciones del transporte que contengan datos sensibles.
        throw new Error(agotado ? "UPSTREAM_TIMEOUT" : "UPSTREAM_UNAVAILABLE");
      }
    };
    try {
      const base = options.env?.url ?? Deno.env.get("SUPABASE_URL");
      if (!base) return respuesta(503);
      const proyecto = new URL(base);
      if (proyecto.protocol !== "https:" || proyecto.username || proyecto.password
        || proyecto.search || proyecto.hash || proyecto.pathname !== "/") return respuesta(503);

      const protegido = withSupabase({
        auth: "user",
        audience: "authenticated",
        issuer: `${proyecto.origin}/auth/v1`,
        env: options.env,
        cors: "disabled", // Ni preflight anonimo ni modos de autenticacion alternativos.
        errors: { detailed: false },
        supabaseOptions: {
          global: { fetch: transporte },
          auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
        },
      }, async (_request, ctx) => {
        const sub = ctx.jwtClaims?.sub;
        if (ctx.authMode !== "user" || ctx.jwtClaims?.role !== "authenticated"
          || typeof ctx.jwtClaims.exp !== "number" || ctx.jwtClaims.exp <= Date.now() / 1000
          || typeof sub !== "string" || !UUID.test(sub) || sub === UUID_CERO) return respuesta(401);

        const actual = await ctx.supabase.auth.getUser(bearer[1]);
        if (actual.error) {
          if (agotado) return respuesta(504);
          if ([401, 403, 404].includes(actual.error.status ?? 0)) return respuesta(401);
          return respuesta([0, 429, 503].includes(actual.error.status ?? 0) ? 503 : 502);
        }
        const user = actual.data.user;
        if (!user || !UUID.test(user.id) || user.id.toLowerCase() !== sub.toLowerCase()) {
          return respuesta(401);
        }

        // Cliente independiente construido por el SDK desde secretos del runtime,
        // no desde el JWT del usuario ni desde parametros HTTP.
        const eliminacion = await ctx.supabaseAdmin.auth.admin.deleteUser(user.id, false);
        if (eliminacion.error) {
          if (agotado) return respuesta(504);
          if (eliminacion.error.status === 404) return respuesta(401);
          // Auth puede envolver Storage como error generico de DB. No adivinar 409
          // mediante textos ni convertir credenciales admin rechazadas en 401 de usuario.
          return respuesta([0, 429, 503].includes(eliminacion.error.status ?? 0) ? 503 : 502);
        }
        // El SDK consulta el usuario antes de DELETE y luego espera su confirmacion.
        // Auth responde {} al DELETE fisico; no exigir un UUID en esa respuesta HTTP.
        if (!confirmadoPorAuth || !eliminacion.data?.user) return respuesta(502);
        return respuesta(204);
      });
      const resultado = await protegido(req);
      // Tambien se ocultan detalles producidos por el wrapper, no solo por Auth.
      if (resultado.status === 204) return respuesta(204);
      if ([400, 401, 502, 503, 504].includes(resultado.status)) return respuesta(resultado.status);
      return respuesta(resultado.status === 403 ? 401 : 503);
    } catch {
      return respuesta(agotado || limite.aborted ? 504 : 503);
    } finally {
      clearTimeout(reloj);
    }
  };
}
