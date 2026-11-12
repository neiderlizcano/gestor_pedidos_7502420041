import { createDeleteMyAccountHandler } from "./handler.ts";

// Solo el runtime de Supabase proporciona credenciales administrativas.
Deno.serve(createDeleteMyAccountHandler());
