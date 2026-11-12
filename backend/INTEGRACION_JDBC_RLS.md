# Integracion JDBC/RLS opcional (solo lectura)

Las pruebas normales siguen ejecutandose con `mvn clean test`, sin PostgreSQL.
`JdbcRlsIT` y `ReportesRlsIT` no coinciden con los nombres de Surefire. Failsafe se incorpora a `verify`
solo con `RUN_DB_INTEGRATION=true`; JUnit exige ademas esa misma variable, incluso
si alguien activa el perfil Maven `db-integration` manualmente. No hay conexiones
ni lecturas de configuracion JDBC en inicializadores estaticos.

Ambas suites reutilizan `SoporteJdbcRlsIT` para la conexion, el rol real, SSL,
solo lectura y errores sin datos sensibles. Se conservan las seis pruebas de
`JdbcRlsIT`; las doce pruebas nuevas de reportes y sus requisitos se describen
en [INTEGRACION_REPORTES_RLS.md](INTEGRACION_REPORTES_RLS.md).

## Preparacion externa

- Proporcionar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `DB_SSL_ROOT_CERT` en el
  entorno del proceso por un mecanismo seguro, no mediante archivos versionados
  ni argumentos Maven. No imprimir el entorno ni activar trazas Maven/JDBC/driver.
- `DB_USERNAME` debe ser `gestor_pedidos_jdbc` para conexion directa o
  `gestor_pedidos_jdbc.[PROJECT_REF]` para Supabase Session pooler (sustituir
  `[PROJECT_REF]` por la referencia del proyecto, en minusculas y digitos).
  El nombre se envia intacto al driver; el sufijo solo enruta la conexion.
  PostgreSQL debe confirmar que `session_user` y `current_role` son exactamente
  `gestor_pedidos_jdbc`, sin el sufijo, y que el rol no tiene privilegios de bypass.
- `DB_URL` debe contener solamente protocolo JDBC PostgreSQL, host, puerto y base;
  sin usuario, contrasena, fragmento ni parametros de URL. Esto impide sobrescribir
  SSL, certificado, modo de solo lectura u otras opciones fijadas por la prueba.
- `DB_SSL_ROOT_CERT` es la ruta a un archivo CA valido y legible. El driver utiliza
  siempre `sslmode=verify-full` y `sslrootcert`; no existe alternativa sin SSL.
- Se requiere una conexion directa o pooler en modalidad sesion para comprobar el
  mismo `pg_backend_pid()` tras reutilizar la conexion. Modalidad transaccion no
  queda validada por esta prueba. No usar credenciales administrativas.
- A/B es opcional: proporcionar **ambos** `DB_TEST_USER_A` y `DB_TEST_USER_B`, UUID
  distintos de perfiles ya existentes en `public.usuarios`. A debe tener al menos
  un pedido preexistente; B puede no tener pedidos. No se crean fixtures ni usuarios
  Auth. Los UUID y el identificador del pedido solo permanecen en memoria durante
  las pruebas.

## Ejecucion desde PowerShell, en la raiz del repositorio

Con las variables anteriores ya proporcionadas de forma segura:

```powershell
try {
    $env:RUN_DB_INTEGRATION = 'true'
    mvn -B -ntp -f backend/pom.xml verify
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la verificacion JDBC/RLS.' }
} finally {
    Remove-Item Env:RUN_DB_INTEGRATION -ErrorAction SilentlyContinue
}
```

No ejecutar este procedimiento durante una tarea que prohiba conexiones reales.

## Alcance y limites

- Rol de conexion dedicado, ausencia de privilegios de bypass, rol efectivo
  `authenticated`, `auth.uid()`, claims locales y RLS activo en ambas tablas.
- Lecturas directas dentro del ejecutor para comprobar RLS sin depender solo de
  los filtros del adaptador; lecturas mediante ambos adaptadores.
- Rechazo sin identidad o con propietario discordante; commit y limpieza de claims;
  cambio de identidad sobre una conexion Hikari reutilizada, con pool maximo uno.
- Rollback provocado por una excepcion sin DML y posterior contexto limpio. El
  ejecutor expulsa conexiones al fallar: no se promete reutilizar la conexion
  fallida ni demostrar la reversion de escrituras.
- Sin A/B preexistente, los UUID sinteticos pueden no tener filas: comprobar que
  no se exponen filas ajenas no prueba por si solo acceso positivo a datos propios.
  A/B exige visibilidad de ambos perfiles propios y de al menos un pedido propio de A;
  no acepta que ambas identidades simplemente obtengan cero registros. Bajo B se
  consulta por identificador ese pedido conocido de A, sin filtro de propietario,
  y se exige que RLS lo oculte. Tambien se comprueba que ninguno vea el perfil ni
  los pedidos del otro, y que los claims se limpien tras cada identidad.
  Este escenario demuestra aislamiento A/B con un unico propietario de pedidos,
  pero no demuestra todavia acceso simetrico a pedidos propios de ambos usuarios.
- Todas las consultas son lecturas; Hikari/driver fuerzan solo lectura y la prueba
  verifica `transaction_read_only=on`. No hay INSERT, UPDATE, DELETE ni limpieza DML.
- Los logs de la JVM de integracion se desactivan y los errores se presentan sin
  causas del driver, UUID ni resultados de filas. No publicar informes sin revision.
- Estas pruebas estan preparadas, no ejecutadas contra PostgreSQL durante su creacion.

**Validacion historica de persistencia:** se aprobaron 264 pruebas locales y 6 pruebas reales de
integracion JDBC/RLS: conexion, contexto de identidad, aislamiento de lectura A/B,
rollback de lectura y reutilizacion de conexiones, segun la cobertura implementada.
Las operaciones reales de escritura (`INSERT`, `UPDATE` y `DELETE`) todavia no
han sido verificadas mediante pruebas de integracion y quedan pendientes.

**Cierre de Fase 5:** la ejecucion local registra 692 pruebas, 0 fallos, 0 errores
y 1 omitida (el test de ausencia de bandera se desactiva cuando esta esta activa).
Las 6 pruebas reales JDBC/RLS aprobadas corresponden a una ejecucion anterior.
La suite real de reportes registra 12 pruebas: 10 aprobadas y 2 de paginacion
omitidas por sus requisitos de datos; BUILD SUCCESS no acredita esos casos omitidos.
Ver [resultados y limitaciones](INTEGRACION_REPORTES_RLS.md#resultados-comprobados-al-cierre-de-fase-5).
Siguen pendientes las pruebas HTTP completas de extremo a extremo contra
PostgreSQL y las operaciones reales de escritura INSERT, UPDATE y DELETE.
