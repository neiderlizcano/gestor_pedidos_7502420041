# Integracion PostgreSQL de los cuatro reportes (opcional y solo lectura)

`ReportesRlsIT` prepara doce pruebas con casos de uso, adaptadores JDBC y
`EjecutorRlsJdbc` reales. No inicia HTTP ni comprueba firmas JWT: coloca la misma
identidad sintetica en Spring Security que `JdbcRlsIT`. Las pruebas HTTP locales
existentes conservan los filtros y el decoder ES256 reales; esta suite no las sustituye.

## Autorizacion y conexion

- Solo se ejecuta con `RUN_DB_INTEGRATION=true`. Failsafe se activa con esa bandera
  y cada clase exige ademas la bandera JUnit, incluso si el perfil Maven se activa
  manualmente. Sin ella no se leen variables JDBC ni certificados ni se conecta.
- Reutiliza `SoporteJdbcRlsIT`, extraido de la configuracion de las seis pruebas
  existentes, sin duplicarlas. Se mantienen `gestor_pedidos_jdbc` como rol real,
  privilegios restringidos y SSL `verify-full`, tanto directo como Session pooler.
- Proporcionar externamente `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y
  `DB_SSL_ROOT_CERT` mediante un mecanismo seguro, nunca archivos versionados,
  argumentos Maven ni trazas. Ver las restricciones exactas de URL, CA y usuario
  en [INTEGRACION_JDBC_RLS.md](INTEGRACION_JDBC_RLS.md).
- Todas las operaciones son SELECT. Hikari y el driver fuerzan solo lectura y se
  verifica `transaction_read_only=on`. No hay creacion de fixtures ni limpieza DML,
  ni cambios de tablas, roles, usuarios o politicas.

## Datos preexistentes y omisiones

No se configuran nombres, correos, estados ni ubicaciones reales en archivos o
variables adicionales. Los filtros se derivan de lecturas propias bajo RLS y
permanecen solo en memoria. Los UUID externos deben ser canonicos y distintos.

| Caso | Requisitos externos |
| --- | --- |
| Rechazo sin identidad o con propietario discordante | Ningun UUID real |
| Estado, rangos inclusivos y ubicacion (filtros individuales y combinados) | `DB_TEST_USER_A` y al menos un pedido propio |
| Paginacion, orden, OFFSET y `haySiguiente` | A con al menos dos pedidos propios; se leen como referencia hasta tres identificadores |
| Paginacion de ubicacion con filtro conservado | A con al menos dos pedidos propios que compartan pais; se elige el pais mediante una lectura propia |
| Paginas vacias y rechazo de rangos invertidos | `DB_TEST_USER_A`; el filtro sintetico se comprueba ausente antes de usarlo |
| Nombre y username exactos/prefijos | Perfil de A; texto del campo de hasta 200 caracteres |
| Prefijos literales `%`, `_`, `!` y `\` | Perfil de A; el resultado esperado se calcula con su propio texto |
| Correo exacto y resultado vacio | Perfil de A con correo de hasta 254 caracteres |
| A/B pedidos | Ambos UUID y perfiles; A con un pedido propio conocido; B puede no tener pedidos |
| A/B usuarios | Ambos perfiles, correos distintos de hasta 254 caracteres y nombres/username de hasta 200 |

Cada prueba sin datos adecuados se omite con un motivo constante. Un UUID mal
formado, un fallo de consulta o una violacion de aislamiento no se convierten en
omisiones. Un perfil/pedido que ya no este disponible o no cumpla el dominio
actual se considera un dato de referencia no apto, sin publicar sus valores.
No se exige que todos los casos pasen con cualquier base: con un solo pedido
de A se omiten ambas pruebas de paginacion entre paginas, no los filtros ni A/B.

Las muestras se seleccionan mediante lecturas independientes de los reportes y
se exige acceso positivo a datos propios donde corresponda; no basta obtener
vacio bajo ambas identidades. En A/B se confirma que RLS oculta por identificador
el pedido/perfil ajeno conocido, ademas del filtro de propietario de los reportes.
Nombres repetidos pueden devolver legitimamente el perfil propio, nunca el ajeno.
El escenario de pedidos con un unico propietario no acredita acceso simetrico
a pedidos propios de A y B. Las lecturas de referencia y los reportes ocurren en
transacciones distintas: ejecutar con datos estables, sin modificaciones concurrentes.

## Comprobacion del ejecutor y proteccion de resultados

Una plantilla JDBC de prueba, con el mismo DataSource, observa cada consulta de
reporte antes y despues de delegar al JDBC real. Exige transaccion Spring activa,
conexion ligada al pool, SELECT con propietario parametrizado, rol efectivo
`authenticated`, `auth.uid()` y claims correctos, solo lectura y RLS activo.
No reemplaza ni simula `EjecutorRlsJdbc`. Tras cada llamada se exige contexto limpio.

Los resultados se verifican con booleanos y mensajes constantes, sin aserciones
que impriman filas, UUID, correos, filtros o causas del driver. Se desactivan los
logs de integracion. No usar trazas Maven/JDBC ni publicar informes sin revision.

## Ejecucion futura desde PowerShell (raiz del repositorio)

**No ejecutar durante una tarea que prohiba conexiones reales.** Con la conexion
y los UUID ya proporcionados externamente, este comando selecciona solo la nueva
suite de integracion (tambien ejecuta las pruebas locales normales):

```powershell
try {
    $env:RUN_DB_INTEGRATION = 'true'
    mvn -B -ntp -f backend/pom.xml '-Dit.test=ReportesRlsIT' verify
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la integracion de reportes.' }
} finally {
    Remove-Item Env:RUN_DB_INTEGRATION -ErrorAction SilentlyContinue
}
```

Para ambas suites, omitir `-Dit.test=ReportesRlsIT`. Las seis pruebas originales
conservan sus requisitos: si se configura A para su prueba A/B, proporcionar B
tambien. Seleccionando solo reportes se puede configurar A y omitir los casos A/B.

Validacion local sin conexiones, con la bandera ausente y sin cargar secretos:

```powershell
mvn -B -ntp -f backend/pom.xml clean test
mvn -B -ntp -f backend/pom.xml '-Dtest=JdbcRlsIT,ReportesRlsIT' test
```

El segundo comando debe omitir las 18 pruebas (6 existentes y 12 nuevas).

## Resultados comprobados al cierre de Fase 5

| Suite | Resultado registrado |
| --- | --- |
| Locales | 692 registradas: 691 ejecutadas sin fallos ni errores y 1 omitida |
| `JdbcRlsIT` | 6 pruebas reales aprobadas en una ejecucion anterior |
| `ReportesRlsIT` | 12 registradas: 10 aprobadas, 2 omitidas, 0 fallos, 0 errores; BUILD SUCCESS |

La omision local corresponde a
`SoporteJdbcRlsSeguridadTest.sinBanderaLaPreparacionSeDetieneAntesDeCrearInfraestructura`:
su condicion `DisabledIfEnvironmentVariable` la desactiva cuando la bandera de
integracion esta activa. No equivale a un fallo ni a una prueba ejecutada.

Las dos omisiones reales corresponden a `ReportesRlsIT`:

- `pedidosPaginadosMantienenOrdenLimiteOffsetYHaySiguiente`: se omite si A tiene
  menos de dos pedidos propios preexistentes (lineas 192-193).
- `pedidosPorUbicacionPaginadosCuandoHayDosCoincidenciasPropias`: requiere dos
  pedidos propios con el mismo pais no vacio; se omite sin ese grupo apto
  (lineas 205-213), o si al releerlo quedan menos de dos referencias (linea 223).

Por tanto, no se ha acreditado todavia contra PostgreSQL la paginacion entre
paginas ni la conservacion del filtro de ubicacion en ese escenario multipagina.
Estas comprobaciones permanecen pendientes hasta disponer de datos preexistentes
adecuados y estables; no se crean registros artificiales para forzarlas.

**Siguen pendientes las pruebas HTTP completas de extremo a extremo**
(JWT validado -> REST -> casos de uso -> JDBC/RLS -> PostgreSQL). Las pruebas
MockMvc/JWKS locales y las lecturas de integracion con identidad sintetica
cubren capas distintas, no toda esa cadena en una misma prueba.
**Tambien siguen pendientes las operaciones reales de escritura INSERT, UPDATE
y DELETE mediante pruebas de integracion.** No se debe inferir cobertura de
escrituras a partir de estas suites de solo lectura ni cobertura total por BUILD SUCCESS.

Estos resultados se documentan a partir de las ejecuciones comprobadas; durante
este cierre no se repite Maven ni se conecta Supabase.
