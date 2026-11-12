# DELETE de cuenta propia mediante Edge Function — preparado, NO desplegado

Rama: `feature/delete-cuenta-propia`. Base:
`16d0b335da7a8fa55d2fdbfbdfe60bb2321ae23f`.

## Unica estrategia activa

`DELETE /api/v1/usuarios/me` -> JWT ES256 existente -> EliminarCuentaPropiaCasoUso
-> puerto EliminarCuenta -> EliminarCuentaHttp -> Edge Function delete-my-account
-> getUser(jwt) -> auth.admin.deleteUser(user.id, false) -> confirmacion -> 204.

Se reutilizan los puertos, caso de uso, identidad y controlador. HTTP/Supabase/JWT
siguen fuera del nucleo. No se exige perfil ni correo. El adaptador HTTP vuelve a
comparar sub con el UUID confiable del caso de uso y reenvia solo el token original.
La funcion deriva el destino de getUser y lo compara con el sub verificado; nunca
recibe un identificador objetivo del cliente. Ambos extremos rechazan parametros
y cuerpos. La funcion rechaza tambien metodos distintos de DELETE y rutas extra.
Spring tambien rechaza Content-Type: no hay representacion de entrada y esto evita
aceptar formularios cuyo cuerpo ya haya sido consumido por un filtro HTTP.
No se utiliza EjecutorRlsJdbc ni una transaccion local para una operacion remota;
los demas adaptadores JDBC y su contexto RLS permanecen intactos.

## Spring: configuracion necesaria para una eventual instalacion autorizada

- Perfil existente `persistencia`.
- `cuenta.edge-function-url` (variable equivalente `CUENTA_EDGE_FUNCTION_URL`):
  URL fija `https://<project-ref>.supabase.co/functions/v1/delete-my-account`.
  Se rechazan HTTP, credenciales en URL, query, fragmento, puerto distinto de 443,
  rutas diferentes y codificacion alternativa de la ruta. Un dominio personalizado
  HTTPS configurado por el administrador tambien es posible; nunca lo elige HTTP.
- `cuenta.publishable-key` (`CUENTA_PUBLISHABLE_KEY`), opcional: solo una clave
  con prefijo `sb_publishable_`. JWT de rol anon, service_role y sb_secret NO se admiten.
  La clave publica, si el gateway la requiere, va en apikey; no autentica al usuario.

La URL es obligatoria y se valida al construir el bean: sin configuracion correcta
el perfil persistencia falla cerrado. No se altero ninguna variable de Render.
Hay un unico bean EliminarCuenta (HTTP). EliminarCuentaJdbc se conserva deprecated
sin @Repository/@Profile: no es una estrategia seleccionable por Spring.

Apache HttpClient5 (version gestionada por Spring Boot) tiene reintentos,
redirecciones, cookies y cache de autenticacion desactivados. Limites: 2 s para
obtener conexion del pool, 3 s para conectar y negociar TLS, 8 s de respuesta/socket. Son limites
por fase/inactividad, no una promesa de plazo total extremo a extremo. No se envian
entidad, UUID o correo; Authorization se construye por peticion y se retira al salir.
No se guardan tokens ni causas sensibles de transporte. No activar logs wire,
cabeceras HTTP o trazas de SDK/proxy que puedan registrar Authorization.

## Funcion y SDK

Archivos: `supabase/config.toml` y `supabase/functions/delete-my-account/`.
`verify_jwt = true` permanece explicito. No usar despliegue --no-verify-jwt.
Dependencias fijadas: @supabase/server 1.9.2 y @supabase/supabase-js 2.119.0;
`deno.lock` registra el grafo. Son paquetes consultados en el registro oficial y
comprobados localmente con Deno 2.9.6. Esto NO acredita el runtime alojado real.

withSupabase utiliza auth user, issuer del proyecto, audience authenticated, CORS
deshabilitado y modo de errores reducido. La funcion ademas exige role authenticated,
exp vigente y UUID canonico no nulo. getUser(jwt) confirma existencia en Auth.
El cliente administrativo independiente es ctx.supabaseAdmin, creado por el SDK
sin propagar Authorization del usuario y con persistencia/refresh desactivados.
Sus claves solo proceden del entorno gestionado por Supabase, nunca de Spring ni
de datos HTTP. El SDK utiliza SUPABASE_URL, SUPABASE_PUBLISHABLE_KEYS,
SUPABASE_SECRET_KEYS y SUPABASE_JWKS (o sus alternativas soportadas).
Verificar disponibilidad mediante administracion segura antes de instalar, sin
copiar ni imprimir valores. No se usaron ni inspeccionaron secretos reales aqui.

El SDK 2.119.0 hace un GET administrativo del mismo UUID antes de su DELETE para
conservar la respuesta publica de deleteUser. No es un reintento. La funcion tiene
un presupuesto compartido de 6 s para todas esas llamadas Auth, fetch sin
redirecciones y ninguna politica propia de reintentos. Exige HTTP 200 en el DELETE
de Auth, ausencia de error SDK y resultado valido; HTTP 202 NO es confirmacion.
Solo entonces responde 204, sin devolver el usuario obtenido por el SDK.
Todos los errores del wrapper/Auth se reemplazan por codigos genericos sin datos.

La funcion puede ser invocada directamente por quien posea un JWT de usuario:
no se presume acceso exclusivo desde Spring. Aplica por si misma todos los
controles de identidad. CORS no se utiliza como mecanismo de autorizacion.

## Errores y confirmacion

| Situacion | Spring |
| --- | --- |
| Cuerpo/query inesperados | 400 |
| Sin JWT o rechazo de usuario por la funcion | 401 |
| UUID del puerto discordante con sub | 403 |
| Contrato inequívoco de conflicto Storage | 409 |
| Respuesta remota inesperada, 2xx distinto de 204 o redireccion | 502 |
| Conexion fallida, 429 o 503 remoto | 503 |
| Timeout local/remoto sin confirmacion | 504 |
| Solo 204 de la funcion tras exito Auth | 204 |

El 409 solo se reconoce con respuesta JSON exacta {"code":"STORAGE_CONFLICT"},
sin campos extra, claves duplicadas ni tokens sobrantes y con limite de 1024 bytes.
**La funcion preparada no emite ese codigo:** no hay una senal estable de Storage
demostrada para clasificar el error de Auth. Un error generico de BD o un 409
generico NO se adivina por textos: se traduce a 502. El contrato 409 queda reservado
para una identificacion inequivoca posteriormente verificada, no implementada aqui.
Errores administrativos 401/403 no se presentan como credenciales malas del usuario.

No se borran primero perfil/pedidos por JDBC. Auth elimina fisicamente la identidad
y PostgreSQL aplica las FK ON DELETE CASCADE existentes en la misma operacion DB.
No hay transaccion distribuida entre Spring, Edge, Auth y Storage. Un timeout o
fallo de red puede ocurrir despues del commit: devolver error no prueba rollback.
No convertir un usuario inexistente o un error desconocido en exito de reintento.

## Storage y JWT antiguos

Estado comunicado: cero objetos; storage.objects no tiene FK protectora hacia Auth;
supabase_auth_admin no tiene USAGE/SELECT de Storage. Esta estrategia no concede
esos permisos ni instala funciones SQL privilegiadas.

La API oficial documenta rechazo de usuarios propietarios de objetos. Validarlo
en integracion antes de habilitar; cero objetos hoy no demuestra su tratamiento
futuro ni garantiza atomicidad frente a uploads concurrentes. No eliminar archivos
ni metadatos por SQL. Objetos sin owner, subidas en curso y rutas de subida activas
requieren revision operativa separada antes de prometer una garantia completa.

Un access JWT antiguo puede seguir siendo aceptado por otros endpoints stateless
hasta exp. Eliminar Auth elimina sesiones y bloquea renovacion normal segun Auth,
pero no revoca criptograficamente JWT emitidos. Este DELETE consulta getUser y
rechaza identidades ya inexistentes. No se modificaron autenticacion, expiraciones
ni UI; la app debe limpiar su sesion local tras 204.

## SQL anterior: solo historico NO EJECUTAR

Contenido SQL conservado sin cambios en:
`docs/historico/NO_EJECUTAR/V20261112_01__delete_cuenta_propia.sql.txt`.
Ya no esta en database/migrations ni tiene extension de migracion SQL ejecutable.
El informe JDBC anterior tambien se archivo en ese directorio y sus 38 pruebas
son historicas, no validacion de esta estrategia. No instalar private, grants,
funciones o politicas de esa propuesta. schema.sql remite solo a este flujo Edge.
No se ejecuto SQL ni se modificaron politicas RLS.

## Verificacion local (sin Supabase)

Java, seleccion explicita de pruebas de este DELETE:

```powershell
mvn -B -ntp -f backend/pom.xml "-Dtest=EliminarCuentaPropiaCasoUsoTest,EliminarCuentaHttpTest,EliminarCuentaHttpConfiguracionTest,EliminarCuentaControladorTest" "-P!db-integration" test
```

Edge, solo tipos y pruebas con fetch simulado, claves ES256 efimeras y entorno
sintetico. El bootstrap sustituye process.env antes de importar std-env; no se
concede acceso al entorno real, red, lectura o escritura durante los tests.

```powershell
npx.cmd --yes deno@2.9.6 check --config supabase/functions/delete-my-account/deno.json supabase/functions/delete-my-account/index.ts
npx.cmd --yes deno@2.9.6 test --config supabase/functions/delete-my-account/deno.json --deny-net --deny-env --deny-read --deny-write supabase/functions/delete-my-account/handler_test.ts
```

La descarga inicial de dependencias publicas no es una conexion al proyecto Supabase.
Resultado final: **82 pruebas Java** (4 caso de uso, 43 adaptador HTTP, 2 cableado,
33 REST) y **38 pruebas Edge**: cero fallos, errores u omisiones. Tipado de index.ts
comprobado con las versiones fijadas. Se corrigieron fixtures durante el desarrollo;
las cifras corresponden a las ejecuciones finales satisfactorias, no a la primera.
No se ejecuta la suite historica completa ni pruebas contra Auth real.

### Inventario de la adaptacion

- Nuevos Java bajo `backend/src/main/java/com/neider/gestorpedidos/`:
  `configuracion/usuarios/EliminarCuentaHttpConfiguracion.java`,
  `usuarios/infraestructura/salida/http/EliminarCuentaHttp.java`,
  `usuarios/aplicacion/error/EliminacionCuentaException.java`.
- Actualizados: controlador/handler REST de DELETE y EliminarCuentaJdbc (inactivo);
  `backend/pom.xml` incorpora HttpClient5. Puertos, caso de uso, identidad y seguridad
  JWT no cambiaron. No se modificaron EjecutorRlsJdbc ni otros adaptadores JDBC.
- Nuevos tests: EliminarCuentaHttpTest y EliminarCuentaHttpConfiguracionTest;
  actualizado EliminarCuentaControladorTest. EliminarCuentaJdbcTest se conserva
  como historial sin ejecutarlo en esta seleccion.
- Edge: config.toml, .gitignore, index.ts, handler.ts, handler_test.ts, deno.json,
  deno.lock. SQL e informe anteriores trasladados al directorio historico.
- Actualizados database/schema.sql, este documento y backend/API_USUARIOS.md.
- Sin fetch, cambio de rama, staging, commit, push, PR, despliegue ni SQL real.
  Sin cambios en Render/Supabase o archivos locales AGENTS.md/opencode.json.

## Validacion posterior separada — requiere autorizacion

1. Revisar runtime/SDK, entorno gestionado, URL fija y gateway ES256 con verify_jwt
   activo en entorno aislado. No desplegar esta funcion sobre produccion sin permiso.
2. Cuentas temporales A/B con perfil/pedidos y otra sin perfil. JWT invalido, UUID
   ajeno, cuerpo/query, metodo incorrecto y cabeceras falsificadas no deben afectar B.
3. DELETE A confirmado: verificar ausencia Auth/perfil/pedidos A y conservacion B,
   tambien desde la sesion propia de B con RLS, no solo desde administracion.
4. Repetir con JWT antiguo, usuario sin perfil, cuenta con objeto Storage y fallos
   de Auth/cascada. No atribuir 409 a errores ambiguos ni confirmar una eliminacion fallida.
5. Simular respuestas perdidas/timeout, comprobar cero reintentos y ausencia de
   tokens/datos en logs. Revisar uploads concurrentes antes de habilitar esa capacidad.
6. Limpiar solo fixtures autorizados. No hay pruebas reales ni despliegue en esta etapa.
