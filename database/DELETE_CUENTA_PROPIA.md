# DELETE de cuenta propia mediante Edge Function — estado de validacion y despliegue

Rama: `feature/delete-cuenta-propia`. Base:
`16d0b335da7a8fa55d2fdbfbdfe60bb2321ae23f`.

## Estado para revision del PR #12

Implementacion publicada hasta `e0be61d8743f51adea26fdb4e487bb089f5b9a26`.
El PR #12 permanece en Draft hacia main. Evidencias comunicadas por el responsable,
no reproducidas durante esta actualizacion documental:

- **Edge Function productiva desplegada** en `vmczbhenravmxvtgzvcv`.
  GET sin JWT y con JWT invalido: HTTP 401.
- **DELETE integral probado con Spring Boot local** contra Supabase de pruebas
  `qqbovvgenqlndocdrhfn`: HTTP 204; cuenta temporal C, perfil y pedidos eliminados,
  con la cuenta B y sus datos conservados. Tambien se verifico el flujo Edge directo
  con una cuenta temporal en pruebas.
- **Render configurado y desplegado correctamente**: health HTTP 200 y endpoint
  protegido sin JWT HTTP 401. El endpoint DELETE de Spring Boot en Render sigue
  **pendiente de incorporar mediante el merge**; esos controles no prueban DELETE.
- **No se ha ejecutado una eliminacion productiva real**. El exito en pruebas no
  constituye una validacion destructiva de produccion.
- **82 pruebas Java y 70 pruebas Edge aprobadas**, con TypeScript validado.
  No se repitieron pruebas durante esta actualizacion.

La Edge productiva puede invocarse directamente con un JWT de usuario valido;
el merge del backend no constituye un interruptor de activacion de esa funcion.

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
La Edge acepta body null o un flujo que termine sin bytes, ignorando chunks vacios;
rechaza cualquier contenido, incluso espacios, errores, abortos y lecturas de mas
de 2 s. Admite chunked vacio y rechaza framing malformado o contradictorio, sin
confiar solo en Content-Length. Los diagnosticos son codigos constantes sin datos.
Spring tambien rechaza Content-Type: no hay representacion de entrada y esto evita
aceptar formularios cuyo cuerpo ya haya sido consumido por un filtro HTTP.
No se utiliza EjecutorRlsJdbc ni una transaccion local para una operacion remota;
los demas adaptadores JDBC y su contexto RLS permanecen intactos.

## Spring: configuracion requerida

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
el perfil persistencia falla cerrado. Render ya esta configurado segun el estado
reportado; esta actualizacion documental no consulta ni modifica sus variables.
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
comprobados localmente con Deno 2.9.6. La validacion alojada comunicada se detalla
arriba; las pruebas locales por si solas no acreditan el runtime de produccion.

withSupabase utiliza auth user, issuer del proyecto, audience authenticated, CORS
deshabilitado y modo de errores reducido. La funcion ademas exige role authenticated,
exp vigente y UUID canonico no nulo. getUser(jwt) confirma existencia en Auth.
El cliente administrativo independiente es ctx.supabaseAdmin, creado por el SDK
sin propagar Authorization del usuario y con persistencia/refresh desactivados.
Sus claves solo proceden del entorno gestionado por Supabase, nunca de Spring ni
de datos HTTP. El SDK utiliza SUPABASE_URL, SUPABASE_PUBLISHABLE_KEYS,
SUPABASE_SECRET_KEYS y SUPABASE_JWKS (o sus alternativas soportadas).
Verificar disponibilidad mediante administracion segura en cada entorno, sin
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
**La funcion no emite ese codigo:** no hay una senal estable de Storage
demostrada para clasificar el error de Auth. Un error generico de BD o un 409
generico NO se adivina por textos: se traduce a 502. El contrato 409 queda reservado
para una identificacion inequivoca posteriormente verificada, no implementada aqui.
Errores administrativos 401/403 no se presentan como credenciales malas del usuario.

No se borran primero perfil/pedidos por JDBC. Auth elimina fisicamente la identidad
y PostgreSQL aplica las FK ON DELETE CASCADE existentes en la misma operacion DB.
No hay transaccion distribuida entre Spring, Edge, Auth y Storage. Un timeout o
fallo de red puede ocurrir despues del commit: devolver error no prueba rollback.
No convertir un usuario inexistente o un error desconocido en exito de reintento.
La eliminacion es fisica e irreversible mediante la API. Revertir el despliegue
no recupera cuentas, perfiles ni pedidos eliminados; una eventual recuperacion
desde respaldos requiere un procedimiento independiente y autorizado.

## Storage y JWT antiguos

Estado historico comunicado para el entorno de pruebas, no una comprobacion actual
de produccion: cero objetos; storage.objects no tiene FK protectora hacia Auth;
supabase_auth_admin no tiene USAGE/SELECT de Storage. Esta estrategia no concede
esos permisos ni instala funciones SQL privilegiadas.

La API oficial documenta rechazo de usuarios propietarios de objetos. Validarlo
en una integracion separada autorizada; cero objetos hoy no demuestra su tratamiento
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
El PR no ejecuta SQL ni modifica politicas RLS. Retirar la definicion antigua de
schema.sql no elimina ni migra una funcion ya instalada en una base existente.

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
33 REST) y **70 pruebas Edge**: cero fallos. Las 70 incluyen las 38 originales y
32 nuevas para cuerpo vacio, framing, lectura acotada, diagnosticos e identidad.
Tipado de index.ts y handler_test.ts comprobado con las versiones fijadas.
Las pruebas locales usan Auth simulado; la validacion integral real en pruebas
es una evidencia separada descrita arriba. No se repitio la suite historica completa.

### Inventario de la adaptacion desde la propuesta JDBC

Este inventario describe las etapas de desarrollo; los puertos y el caso de uso
tambien son archivos nuevos del PR frente a main.

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
- Correccion posterior HTTP 400: handler.ts y handler_test.ts aceptan flujos
  realmente vacios, con plazo de lectura y sin debilitar la identidad autenticada.
- Implementacion publicada en dos commits; PR #12 abierto en Draft. Los despliegues
  y validaciones posteriores se distinguen en el estado inicial de este documento.
  Esta actualizacion solo cambia documentacion, no Render/Supabase ni archivos locales
  AGENTS.md/opencode.json.

## Pendientes posteriores — requieren autorizacion

1. Revisar y fusionar el PR #12 solo con autorizacion; observar el despliegue
   automatico de main en Render y confirmar el commit desplegado. No considerar
   el health previo como evidencia del endpoint DELETE incorporado.
2. Cualquier validacion productiva de DELETE requiere permiso separado y una cuenta
   temporal exclusivamente autorizada. No usar cuentas reales ni borrar B.
3. Conservar la evidencia de eliminacion y aislamiento obtenida en pruebas; no
   repetir eliminaciones como parte de una revision documental.
4. Ampliar la validacion, si se autoriza, con JWT antiguo, usuario sin perfil,
   cuenta con objeto Storage y fallos
   de Auth/cascada. No atribuir 409 a errores ambiguos ni confirmar una eliminacion fallida.
5. Simular respuestas perdidas/timeout, comprobar cero reintentos y ausencia de
   tokens/datos en logs. Revisar uploads concurrentes antes de habilitar esa capacidad.
6. Limpiar solo fixtures autorizados. Permanecen sin acreditar la eliminacion
   productiva real y los escenarios adicionales no incluidos en las evidencias.
