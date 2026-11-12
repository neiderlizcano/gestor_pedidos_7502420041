# Preparacion para Render (Docker, sin desplegar)

Esta tarea solo prepara archivos y validaciones locales. No crea un servicio,
no sube secretos ni conecta Supabase. Crear el servicio en Render puede iniciar
el primer despliegue: realizar los pasos del panel **solo cuando se autorice**.
No hay commit ni push de esta preparacion; Render no puede usar cambios locales.

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

## Pasos exactos del futuro despliegue

Antes de abrir el panel: revisar y publicar estos cambios mediante un proceso
Git autorizado aparte; elegir la rama remota que realmente los contenga. La rama
local de preparacion es `feature/despliegue-render`, aun sin commit/push.

1. En Render: **New > Web Service > conectar repositorio Git**
   `neiderlizcano/gestor_pedidos_7502420041`. Seleccionar la rama remota autorizada
   con esta preparacion (no elegir una integradora que aun no la incluya).
2. Establecer los siguientes campos antes de crear el servicio:

   | Campo | Valor |
   | --- | --- |
   | Language / Runtime | `Docker` |
   | Root Directory | `backend` |
   | Dockerfile Path | `./Dockerfile` |
   | Docker Build Context Directory | `.` |
   | Docker Command | Vacio: conservar `ENTRYPOINT` |
   | Health Check Path | `/actuator/health` |
   | Auto-Deploy | `Off` (o deshabilitado) para controlar cambios posteriores |

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
5. Tras el despliegue, comprobar HTTPS `GET /actuator/health` sin Bearer: `200`
   y `{"status":"UP"}` sin detalles. Comprobar ademas el rechazo sin JWT de las
   rutas protegidas. No imprimir ni copiar tokens/credenciales a logs o tickets.
6. Validar por separado HTTP completo, escrituras y aislamiento con dos usuarios
   antes de afirmar un despliegue funcional. Siguen vigentes los pendientes de
   [Fase 5](INTEGRACION_REPORTES_RLS.md#resultados-comprobados-al-cierre-de-fase-5).

## Validacion local sin base de datos

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

**Limitacion de esta preparacion:** Docker no esta disponible en PATH en el
equipo de trabajo; no se ejecuta Docker ni se acredita una imagen construida.
El empaquetado Maven local no sustituye la validacion de imagen/grupo/montaje.
En un equipo con Docker, validar posteriormente (sin secretos ni integraciones):

```powershell
docker build -f backend/Dockerfile -t gestor-pedidos:render backend
docker run --rm -p 8080:8080 -e PORT=8080 -e SUPABASE_AUTH_ISSUER=https://supabase.invalid/auth/v1 -e SUPABASE_JWKS_URI=https://supabase.invalid/auth/v1/.well-known/jwks.json gestor-pedidos:render
```

No activar `persistencia` en ese smoke test; comprobar health desde otra terminal.
No declara validada la conectividad, CA real ni permisos del montaje de Render.

## Resultado local de la tarea 6.1

- `clean test`: **693 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
  No se generaron reportes Failsafe ni se ejecutaron las suites JDBC/RLS reales.
- `dependency:go-offline package -DskipTests`, con `db-integration` desactivado:
  **BUILD SUCCESS** sin variables reales DB/Supabase ni CA. Bytecode Java 17
  (major 61); JAR sin entradas de certificados ni `.env`.
- Smoke test del JAR con Java 17, issuer/JWKS `.invalid` y sin perfil/DB/CA:
  `PORT` inyectado respetado, listener en todas las interfaces, health publico
  `200 UP` y `/api/v1/pedidos` sin JWT devuelve `401`. Solo peticiones loopback.
- `git diff --check` sin errores. Sin secretos incorporados, conexiones Supabase,
  despliegue, commit ni push. Docker y montaje CA real siguen **sin validar**.

Referencias oficiales: [Docker](https://render.com/docs/docker),
[secretos Docker](https://render.com/docs/docker-secrets),
[variables/archivos](https://render.com/docs/configure-environment-variables),
[monorepos](https://render.com/docs/monorepo-support),
[puertos](https://render.com/docs/web-services#port-binding),
[health](https://render.com/docs/health-checks).
