# Despliegue en Render (Docker)

## Estado actual y evidencias reportadas

Servicio publico: https://gestor-pedidos-api.onrender.com

Historicamente, el servicio fue desplegado y probado desde `feature/despliegue-render`, con el
perfil Spring `persistencia`. La preparacion Docker/configuracion esta publicada
en el commit `ba5df616ffeb99181bb4258f1711f342c2883024`.
La integracion del backend en `main` esta completada mediante el PR #10,
commit `5f533c63f66bc16ad136bbdaf6782cb0e4303983`.
Segun el estado reportado por el responsable, Render esta configurado para
desplegar desde `main`, con Root Directory `backend/` y Auto-Deploy `On Commit`.
El servicio esta `Live`; el ultimo commit desplegado visible reportado es
`ba5df61`. Esto no demuestra un despliegue automatico desde `main`.
**La prueba de activacion automatica por un nuevo commit sigue pendiente.**
Esta actualizacion es documental: no cambia Render ni realiza otro despliegue.

Evidencias historicas de ejecucion real reportadas por el responsable del proyecto;
no reproducidas durante esta actualizacion ni atribuidas al nuevo flujo automatico
desde `main`:

| Comprobacion | Resultado reportado |
| --- | --- |
| `GET /actuator/health` | `200`, estado `UP` |
| Autenticacion JWT | JWT ES256 validado correctamente |
| Acceso a ruta protegida sin token | `401` |
| `GET /api/v1/pedidos` con JWT | `200` |
| `POST /api/v1/usuarios` con JWT | `201`, perfil creado |
| `GET /api/v1/usuarios/me` con JWT | `200` |
| `PUT /api/v1/usuarios/me` con JWT | `200`, nombre actualizado y persistido |

Tambien se reporto el inicio correcto de la conexion JDBC. Estas evidencias
acreditan los flujos indicados, no el CRUD completo, los cuatro reportes ni el
aislamiento de extremo a extremo con dos usuarios. Los pendientes de Fase 5 no
se consideran cerrados por estos resultados parciales. No se publican tokens,
credenciales, datos personales ni contenido del certificado.

## Construccion y ejecucion

- Contexto Docker: `backend/`, no la raiz del repositorio. El Dockerfile copia
  `pom.xml` y `src`, compila con Maven/Java 17 y ejecuta con un JRE 17 como `app`
  (no root). No declarar `ARG` ni `ENV` con credenciales.
- Ambas invocaciones Maven desactivan explicitamente `db-integration`;
  `clean package -DskipTests` no ejecuta pruebas ni arranca Spring. Solo descarga
  dependencias/plugins publicos de Maven: no requiere Supabase, variables JDBC,
  JWT, certificado CA ni `RUN_DB_INTEGRATION` en el build.
- `.dockerignore` permite unicamente el POM y el arbol `src`, excluyendo ademas
  formatos habituales de certificados/credenciales dentro de ese arbol. Excluye
  los Secret Files que Render coloca en la raiz del contexto. Revisar cualquier
  recurso nuevo: nunca guardar secretos en fuentes/configuracion ni nombrarlos
  `pom.xml` (nombre reservado al descriptor Maven).
- Spring escucha en `0.0.0.0:${PORT:8080}`. `EXPOSE 8080` es informativo,
  no fija el puerto de Render. No hace falta sobrescribir el comando Docker.
- Sin perfil `persistencia`, no se crea infraestructura JDBC ni se necesitan
  las cuatro variables `DB_*`. El issuer/JWKS sigue siendo obligatorio; la
  seguridad JWT ES256 no se desactiva para arrancar sin base de datos.
- `GET /actuator/health` permanece publico y sin detalles. Con `persistencia`,
  el indicador JDBC de salud puede abrir una conexion: configurar primero DB/CA.
  Un health `UP` no acredita CRUD, JWT completo ni aislamiento RLS de extremo a extremo.

## Variables de ejecucion en Render

Configurar en **Environment**, no en el Dockerfile ni en archivos versionados.
Los textos entre `<...>` son marcadores, no valores utilizables.

| Variable | Valor / uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `persistencia` para habilitar JDBC y la API persistente. No es un perfil Maven. |
| `PORT` | `10000` (valor habitual de Render); Spring lo respeta. En local, ausente implica `8080`. |
| `SUPABASE_AUTH_ISSUER` | `https://<project-ref>.supabase.co/auth/v1`, issuer exacto de los JWT del proyecto. |
| `SUPABASE_JWKS_URI` | `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`, JWKS para ES256. |
| `DB_URL` | `jdbc:postgresql://<host>:5432/postgres`, host real de conexion directa o Session pooler obtenido del panel de Supabase. Sin usuario, password, parametros ni fragmentos. |
| `DB_USERNAME` | Directa: `gestor_pedidos_jdbc`. Session pooler: `gestor_pedidos_jdbc.<project-ref>`; el sufijo enruta, el rol PostgreSQL real sigue siendo `gestor_pedidos_jdbc`. |
| `DB_PASSWORD` | Password del rol dedicado, suministrado por canal seguro. No es la password de `postgres` ni una clave de API. |
| `DB_SSL_ROOT_CERT` | `/etc/secrets/supabase-ca.crt`, ruta absoluta del Secret File en ejecucion. |

No configurar `RUN_DB_INTEGRATION`, UUID de pruebas ni perfiles Maven en Render.
No usar `postgres`, `service_role`, anon keys ni tokens JWT como credenciales JDBC.
No habilitar logs de entorno, trazas JDBC/driver ni detalles de salud publicos.

## PostgreSQL y certificado CA externo

1. Obtener por un canal confiable el certificado CA PEM que valida el servidor
   de la conexion elegida. Usar la CA correcta para ese host; no una clave privada
   ni cualquier certificado encontrado en Internet. No guardarlo en el repositorio.
2. En Render, **Environment > Secret Files > Add Secret File**, crear el archivo
   `supabase-ca.crt` y pegar unicamente la CA publica. Su ruta de ejecucion sera
   `/etc/secrets/supabase-ca.crt`; asignarla a `DB_SSL_ROOT_CERT`.
3. El archivo se monta en ejecucion: no agregar `COPY`, mounts de build, Base64,
   argumentos Maven ni variables con el contenido del certificado. La imagen
   no debe contener la CA. `app` pertenece al grupo `1000` para leer el montaje
   de Render sin ejecutar Java como root ni abrir permisos globalmente.
4. Hikari pasa `sslmode=verify-full` y `sslrootcert=${DB_SSL_ROOT_CERT}` al driver.
   El pool es diferido: la creacion del contexto no comprueba presencia, contenido
   ni permisos de la CA. Se validan al abrir conexion; una CA ausente o invalida
   impide conectar, no habilita una alternativa sin verificacion.
   No rebajar SSL para resolver fallos de CA, hostname o permisos. No agregar
   opciones SSL/credenciales a `DB_URL` que puedan sobrescribir esta configuracion.
5. Usar el rol dedicado ya preparado, sin privilegios de bypass, conservando
   JWT ES256, contexto por transaccion y RLS existentes. Esta tarea no crea ni
   administra roles ni migra la base. Ver [requisitos JDBC/RLS](INTEGRACION_JDBC_RLS.md).
6. Si la conexion directa requiere IPv6 no disponible desde el servicio, elegir
   el **Session pooler** compatible con su red y la CA de ese endpoint. No usar
   Transaction pooler: su comportamiento no ha sido validado para este backend.

## Referencia de configuracion y despliegues posteriores

El servicio existente tiene configurada la rama `main` y Auto-Deploy `On Commit`,
segun el estado reportado; esta tarea no consulta ni modifica el panel. Los pasos
siguientes se conservan como referencia para recrearlo o realizar despliegues
posteriores autorizados; no implican crear otro servicio durante esta tarea.
La integracion y la configuracion estan completadas; queda pendiente verificar
que un nuevo commit integrado en `main` active el despliegue sin accion manual,
registrando el commit disparador y el commit desplegado. La preparacion documental
en una rama de trabajo no demuestra esa activacion.

1. En Render: **New > Web Service > conectar repositorio Git**
   `neiderlizcano/gestor_pedidos_7502420041`. Seleccionar `main`, que ya contiene
   el backend y la preparacion del despliegue.
2. Establecer los siguientes campos antes de crear el servicio:

   | Campo | Valor |
   | --- | --- |
   | Language / Runtime | `Docker` |
   | Root Directory | `backend` |
   | Dockerfile Path | `./Dockerfile` |
   | Docker Build Context Directory | `.` |
   | Docker Command | Vacio: conservar `ENTRYPOINT` |
   | Health Check Path | `/actuator/health` |
   | Branch | `main` |
   | Auto-Deploy | `On Commit`; prueba de activacion automatica pendiente |

   Elegir nombre, region y plan adecuados. Las rutas Docker son relativas a
   `backend`, por lo que no escribir `backend/Dockerfile` ni usar contexto raiz.
   No definir Build Command/Start Command de un runtime nativo.
3. Cargar las ocho variables de la tabla y el Secret File **antes del primer
   arranque** (en las opciones avanzadas de creacion si estan disponibles).
   Si el panel exige crear primero, hacerlo solo en la fase autorizada y no
   considerar valido ningun arranque sin DB/CA; completar Environment y lanzar
   el despliegue manual. Desactivar Auto-Deploy no evita el primer despliegue.
4. Solo con autorizacion: pulsar **Create Web Service / Deploy**. En servicios
   existentes, guardar entorno/archivos puede iniciar despliegue; preferir
    **Save only**, si se ofrece, y luego **Manual Deploy > Deploy latest commit**.
    Este procedimiento manual no sirve como evidencia de Auto-Deploy. Para la
    prueba pendiente, observar el despliegue disparado por un nuevo commit en
    `main` sin usar **Manual Deploy**; no se realiza esa prueba en esta tarea.
5. Tras el despliegue, comprobar HTTPS `GET /actuator/health` sin Bearer: `200`
   y `{"status":"UP"}` sin detalles. Comprobar ademas el rechazo sin JWT de las
   rutas protegidas. No imprimir ni copiar tokens/credenciales a logs o tickets.
6. Validar por separado HTTP completo, escrituras y aislamiento con dos usuarios
   antes de afirmar un despliegue funcional. Siguen vigentes los pendientes de
   [Fase 5](INTEGRACION_REPORTES_RLS.md#resultados-comprobados-al-cierre-de-fase-5).

## Referencia de validacion local sin base de datos

Los comandos siguientes documentan el procedimiento local; no se repiten durante
esta consolidacion. Los resultados historicos se conservan al final.

Con `RUN_DB_INTEGRATION`, `SPRING_PROFILES_ACTIVE`, `SPRING_APPLICATION_JSON`,
variables `DB_*` y configuracion externa retiradas del **proceso hijo**, ejecutar
desde la raiz (sin inspeccionar sus valores):

```powershell
mvn -B -ntp -f backend/pom.xml '-P!db-integration' clean test
mvn -B -ntp -f backend/pom.xml '-P!db-integration' package -DskipTests
git diff --check
```

Las pruebas usan issuer/JWKS sinteticos y servidores JWKS locales: no requieren
Supabase. Comprueban arranque sin DataSource, health HTTP publico, puerto y
binding de CA ficticia sin leer certificados ni abrir el pool.

**Limitacion de la validacion local:** Docker no estuvo disponible en PATH en el
equipo de preparacion; no se construyo ni ejecuto la imagen localmente.
Esto no niega el despliegue real posterior reportado en Render.
El empaquetado Maven local no sustituye la validacion de imagen/grupo/montaje.
En un equipo con Docker, validar posteriormente (sin secretos ni integraciones):

```powershell
docker build -f backend/Dockerfile -t gestor-pedidos:render backend
docker run --rm -p 8080:8080 -e PORT=8080 -e SUPABASE_AUTH_ISSUER=https://supabase.invalid/auth/v1 -e SUPABASE_JWKS_URI=https://supabase.invalid/auth/v1/.well-known/jwks.json gestor-pedidos:render
```

No activar `persistencia` en ese smoke test; comprobar health desde otra terminal.
No declara validada la conectividad, CA real ni permisos del montaje de Render.

## Resultado local historico de la tarea 6.1 (previo al despliegue)

- `clean test`: **693 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
  No se generaron reportes Failsafe ni se ejecutaron las suites JDBC/RLS reales.
- `dependency:go-offline package -DskipTests`, con `db-integration` desactivado:
  **BUILD SUCCESS** sin variables reales DB/Supabase ni CA. Bytecode Java 17
  (major 61); JAR sin entradas de certificados ni `.env`.
- Smoke test del JAR con Java 17, issuer/JWKS `.invalid` y sin perfil/DB/CA:
  `PORT` inyectado respetado, listener en todas las interfaces, health publico
  `200 UP` y `/api/v1/pedidos` sin JWT devuelve `401`. Solo peticiones loopback.
- `git diff --check` sin errores. En aquella fase local no se incorporaron secretos
  ni se realizaron conexiones Supabase, despliegue, commit o push. Posteriormente
  se publico la preparacion y se reporto el despliegue descrito al inicio.
  Docker local y permisos del montaje CA no tienen validacion especifica registrada.

Referencias oficiales: [Docker](https://render.com/docs/docker),
[secretos Docker](https://render.com/docs/docker-secrets),
[variables/archivos](https://render.com/docs/configure-environment-variables),
[monorepos](https://render.com/docs/monorepo-support),
[puertos](https://render.com/docs/web-services#port-binding),
[health](https://render.com/docs/health-checks).
