# HISTORICO — DELETE JDBC descartado — NO EJECUTAR

Este documento conserva el diseno anterior y sus 38 pruebas, NO la estrategia
vigente. Ninguna instruccion de instalacion de este archivo sigue activa.
Ver [documentacion actual](../../../database/DELETE_CUENTA_PROPIA.md).
El SQL referido se conserva como `.sql.txt` en este mismo directorio NO EJECUTAR.

Base: `16d0b335da7a8fa55d2fdbfbdfe60bb2321ae23f`.
No hay Flyway/Liquibase ni aplicacion automatica de esta migracion.

## Contrato y arquitectura

`DELETE /api/v1/usuarios/me`, sin cuerpo (incluido whitespace), query string ni
parametros de formulario. Cabeceras de identidad ajenas no seleccionan destino.
Spring Security existente valida ES256, emisor, tiempos y claims; no se modifica.
Controlador -> EliminarCuentaPropia -> caso de uso -> EliminarCuenta -> JDBC ->
EjecutorRlsJdbc -> `SELECT private.delete_my_account()` sin argumentos.
Un puerto UUID separado evita exigir email como hace el puerto de perfiles.
No se consulta `public.usuarios`: se puede eliminar Auth sin perfil.

El ejecutor vuelve a comparar el UUID con UsuarioAutenticado, rechaza transacciones
externas, usa el mismo DataSource, ejecuta SET LOCAL ROLE authenticated y fija sub
y claims locales, verifica auth.uid()/RLS y hace commit antes de retornar.
El controlador solo entonces devuelve 204 sin cuerpo. Excepciones SQL, fallo de
commit, funcion ausente, identidad inexistente o Storage producen error, nunca 204.
Los errores REST son genericos: 400 entrada, 401 autenticacion, 403 contexto
discordante, 500 fallo de persistencia/commit. No se publica SQL ni causas internas.
La configuracion JDBC existente usa Hikari y JdbcTransactionManager compartidos,
optativos bajo el perfil persistencia; no se modifica ni se consulta su entorno.
La funcion confia en los claims que establece ese backend: quien comprometa las
credenciales JDBC puede establecer otro sub bajo authenticated. No verifica firmas
JWT en PostgreSQL; el limite de confianza es la cadena JWT de Spring y su login
dedicado. No equivale a aislar usuarios de un atacante con credenciales de BD.

## Migracion propuesta y puertas de instalacion

`migrations/V20261112_01__delete_cuenta_propia.sql` es SQL manual versionado.
No ejecutar `schema.sql` sobre una base existente para migrarla (recrea politicas).
El esquema base deja de crear la funcion publica antigua; no hay DROP preparado
ni ejecutado. La migracion aborta si encuentra esa firma publica o un esquema
private preexistente: investigar sin sobrescribir y pedir autorizacion separada.

Propietario propuesto: **supabase_auth_admin existente**, por ser el propietario
controlado de las operaciones Auth; nunca el login JDBC ni authenticated.
No se crean roles ni membresias. Antes de autorizar se debe confirmar que ese rol
esta bajo control exclusivo de Auth/administracion, que el login JDBC NO puede
asumirlo, y que su uso como definidor esta permitido en este proyecto Supabase.
No se autoriza concederle nuevos privilegios administrativos ni BYPASSRLS.
Este rol tiene privilegios Auth amplios: riesgo inherente de SECURITY DEFINER que
requiere aprobacion del administrador, no una garantia de minimo privilegio global.

Permisos: propietario con USAGE auth y EXECUTE auth.uid(), SELECT(id), DELETE en
auth.users y visibilidad completa; USAGE storage y SELECT storage.objects para
rechazar objetos propios. Se concede esa lectura explicitamente, pero **no** se
crean/modifican politicas. authenticated recibe solo USAGE private y EXECUTE de
esa funcion. PUBLIC/anon/service_role/login JDBC no reciben EXECUTE directo.
El propietario conserva sus derechos inherentes. Revocar privilegios directos
no elimina membresias ni privilegios heredados: auditar tambien estos ultimos.
gestor_pedidos_jdbc requiere la membresia SET de authenticated ya utilizada por el
ejecutor, NO DELETE auth.users, NO USAGE/CREATE private directo ni rol propietario.
La funcion ademas exige session_user gestor_pedidos_jdbc y role authenticated.
No es una RPC publica de Supabase ni debe exponerse private en Data API.

**Bloqueo pendiente importante:** no se ha demostrado que supabase_auth_admin
pueda leer Storage sin filtrado RLS. La funcion rechaza row_security_active=true
en Auth o Storage aun si alguna politica pudiera permitir lectura completa.
La migracion comprueba esta condicion bajo SET LOCAL ROLE y aborta antes de crear
la funcion, revirtiendo tambien concesiones; la funcion repite la defensa en runtime.
Si ocurre, esta propuesta no es instalable/operativa sin una decision posterior
de seguridad sobre el propietario o un mecanismo Storage revisado. No resolver
agregando BYPASSRLS, roles administrativos, politicas o credenciales a Spring.
No se afirma que la migracion haya sido validada por PostgreSQL real.

private debe permanecer fuera de los esquemas expuestos de Supabase Data API;
CREATE SCHEMA no configura esa lista. Confirmarlo en configuracion administrativa
antes de instalar, sin cambiarla en esta etapa. Comprobar tambien los default ACL
del instalador y privilegios efectivos adicionales antes del COMMIT autorizado.

## Atomicidad, Storage y JWT

La funcion no tiene parametros ni SQL dinamico y usa search_path vacio, tablas
y funciones cualificadas. Rechaza UUID nulo/invalido/cero y elimina exactamente
auth.uid(); el email nunca interviene. DELETE Auth y cascadas son una transaccion.
Un perfil faltante no impide la operacion; una identidad Auth faltante falla.
Un trigger o FK futuro puede abortar todo: revisar siempre antes de instalar.

Estado comunicado, NO reconsultado: cero objetos Storage y cero triggers DELETE
personalizados. La comprobacion owner_id contempla objetos futuros con propietario.
No borra Storage ni detecta objetos sin owner que el negocio asocie por rutas.
**Carrera pendiente:** Storage no garantiza una FK de owner_id a Auth; el bloqueo
de la fila Auth no serializa uploads. Antes de habilitar DELETE se necesita una
ventana sin escrituras Storage o un protocolo revisado que impida uploads durante
la eliminacion y defina objetos sin owner. No se promete atomicidad entre Storage
y PostgreSQL. Tener cero objetos hoy no resuelve esta puerta para el futuro.

Eliminar Auth impide uso normal posterior de esa identidad/refresh segun Auth,
pero NO revoca criptograficamente un access JWT ES256 ya emitido: la API stateless
puede aceptarlo hasta expirar. RLS por UUID no comprueba existencia en Auth.
Este DELETE repetido con JWT antiguo falla sin falsa confirmacion; otros endpoints
pueden seguir autorizando operaciones que no requieren filas Auth. No se cambia
autenticacion ni se implementa blacklist/introspeccion. La app debe limpiar sesion
local tras 204; esa UI queda fuera de este cambio. Revisar requisitos de revocacion
inmediata antes de habilitar en produccion.

## Consultas posteriores (solo preparar; NO ejecutadas)

Ejecutar en una revision autorizada; no imprimir secretos ni claims reales.

```sql
SELECT n.nspname, pg_catalog.pg_get_userbyid(n.nspowner) AS owner, n.nspacl
FROM pg_catalog.pg_namespace n WHERE n.nspname = 'private';
SELECT p.oid::pg_catalog.regprocedure, pg_catalog.pg_get_userbyid(p.proowner),
       p.prosecdef, p.proconfig, p.proacl, pg_catalog.pg_get_functiondef(p.oid)
FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname IN ('public','private') AND p.proname = 'delete_my_account';
SELECT pg_catalog.has_schema_privilege('authenticated','private','USAGE'),
       pg_catalog.has_schema_privilege('authenticated','private','CREATE'),
       pg_catalog.has_function_privilege('authenticated','private.delete_my_account()','EXECUTE'),
       pg_catalog.has_function_privilege('anon','private.delete_my_account()','EXECUTE'),
       pg_catalog.has_table_privilege('gestor_pedidos_jdbc','auth.users','DELETE');
SELECT rolname, rolsuper, rolbypassrls, rolcreaterole, rolcanlogin
FROM pg_catalog.pg_roles
WHERE rolname IN ('gestor_pedidos_jdbc','authenticated','supabase_auth_admin');
SELECT * FROM pg_catalog.pg_auth_members
WHERE member IN (SELECT oid FROM pg_catalog.pg_roles
                 WHERE rolname IN ('gestor_pedidos_jdbc','authenticated'));
-- Solo en sesion administrativa autorizada, NO desde Spring:
BEGIN;
SET LOCAL ROLE supabase_auth_admin;
SELECT pg_catalog.row_security_active('auth.users'::pg_catalog.regclass),
       pg_catalog.row_security_active('storage.objects'::pg_catalog.regclass);
ROLLBACK;
SELECT conrelid::pg_catalog.regclass, conname, confdeltype
FROM pg_catalog.pg_constraint
WHERE confrelid = 'auth.users'::pg_catalog.regclass AND contype = 'f';
SELECT tgname, tgenabled FROM pg_catalog.pg_trigger
WHERE tgrelid = 'auth.users'::pg_catalog.regclass AND NOT tgisinternal;
```

Revisar ademas RLS, grants/default ACL, triggers DELETE, storage.objects.owner_id,
sesion/pool, firmas sin sobrecargas inseguras y lista de esquemas Data API.
No invocar la funcion como consulta de verificacion: elimina la identidad.

## Plan separado de integracion destructiva (requiere autorizacion posterior)

1. Solo entorno aislado, instalar tras resolver puertas anteriores y autorizar
   SQL explicitamente. Crear cuentas temporales A/B, perfiles/pedidos en ambos;
   preparar otra cuenta temporal sin perfil. Registrar conteos sin publicar PII.
2. Con JWT A: rechazar id/usuario_id/email en query, UUID en ruta y cualquier
   cuerpo; cabecera X-Usuario-Id=B no debe seleccionar B. JWT ausente, expirado,
   firma/emisor/claims invalidos: 401 y ninguna invocacion JDBC.
3. Dentro de contexto JDBC A, intentar propietario B: rechazo antes del SQL.
   Sin claims, claims invalidos, rol anon/login directo y funcion con argumentos:
   rechazo; comprobar privilegios efectivos, no solo grants textuales.
4. DELETE A valido: 204 solo tras commit; verificar con observador autorizado
   ausencia Auth/perfil/pedidos A y conservacion exacta de B. Verificar B desde
   su propia sesion RLS tambien. No basar aislamiento solo en sesion administrativa.
5. Cuenta sin perfil: DELETE elimina Auth. Repetir con JWT antiguo: error y B
   intacta; probar/registrar limitaciones de access JWT y refresh tras eliminacion.
6. Usar otras cuentas desechables para fallo deliberado de transaccion/trigger
   en entorno aislado: comprobar rollback Auth/cascadas, no 204, pool/contexto
   limpios y error REST sin SQL. Simular fallo de commit y respuesta perdida:
   un error de red puede ocurrir despues de commit, no prometer reintento exitoso.
7. Con objeto Storage propio, exigir fallo y conservacion completa de la cuenta.
   Probar visibilidad RLS, objetos sin owner y uploads concurrentes solo despues
   de aprobar un protocolo que cierre la carrera. No borrar Storage por SQL.
8. Reutilizar conexion A/B y confirmar limpieza de rol/claims tras commit/rollback.
   Limpiar fixtures exclusivamente con alcance y autorizacion explicitos.

Nada de este plan fue ejecutado contra Supabase en esta etapa.

## Pruebas locales realizadas

```powershell
mvn -B -ntp -f backend/pom.xml "-Dtest=EliminarCuentaPropiaCasoUsoTest,EliminarCuentaJdbcTest,EliminarCuentaControladorTest" "-P!db-integration" test
```

Resultado final: **38 pruebas, 0 fallos, 0 errores, 0 omitidas** (4 caso de uso,
7 adaptador/ejecutor, 27 REST). JDBC y gestor transaccional simulados; protocolo
EjecutorRlsJdbc real, cadena de seguridad y verificador ES256 reales con claves
efimeras y JWKS en loopback. No se consultaron claves/tokens/certificados reales.
La simulacion de commit prueba propagacion/orden, no durabilidad PostgreSQL.
No se ejecutaron las 693 pruebas historicas, migraciones ni integraciones reales.

## Archivos de la implementacion

Modificado: `database/schema.sql` (solo retira la definicion de la funcion publica;
no se cambian las politicas).

Nuevos bajo `backend/src/main/java/com/neider/gestorpedidos/`:

- `configuracion/usuarios/EliminarCuentaConfiguracion.java`
- `usuarios/aplicacion/puerto/entrada/EliminarCuentaPropia.java`
- `usuarios/aplicacion/puerto/salida/EliminarCuenta.java`
- `usuarios/aplicacion/puerto/salida/IdentidadCuentaAutenticada.java`
- `usuarios/aplicacion/caso/EliminarCuentaPropiaCasoUso.java`
- `usuarios/infraestructura/salida/jdbc/EliminarCuentaJdbc.java`
- `usuarios/infraestructura/entrada/rest/EliminarCuentaControlador.java`
- `usuarios/infraestructura/entrada/rest/EliminarCuentaErroresHandler.java`

Nuevos bajo `backend/src/test/java/com/neider/gestorpedidos/`:

- `usuarios/aplicacion/EliminarCuentaPropiaCasoUsoTest.java`
- `configuracion/persistencia/EliminarCuentaJdbcTest.java`
- `usuarios/infraestructura/entrada/rest/EliminarCuentaControladorTest.java`

Tambien nuevos: este documento y
`database/migrations/V20261112_01__delete_cuenta_propia.sql`.
No hubo commit, push, PR, despliegue, SQL real ni eliminacion de cuentas.
AGENTS.md/opencode.json permanecen sin seguimiento y no se editaron.
