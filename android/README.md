# Gestor de Pedidos — Android nativo

Aplicación Android nativa del proyecto Gestor de Pedidos (Actividad 3, Universidad de Cartagena), escrita en Kotlin con Jetpack Compose. Es independiente de la aplicación Kodular del mismo proyecto y usa el identificador `com.neiderlizcano.gestorpedidos.nativo`.

## Estado actual

### Implementado
- Pantalla de inicio en Jetpack Compose.
- Estado de interfaz con MVVM y `StateFlow`, observado según el ciclo de vida.
- Diálogo «Conocer la aplicación».
- Tema base claro/oscuro.
- Pruebas unitarias de `HomeViewModel`.
- Infraestructura HTTP, DTO, contratos y repositorios de usuarios, pedidos y reportes, todavía sin conectar a pantallas.

### Previsto, no implementado
- Inicio de sesión y autenticación.
- Pantallas de CRUD de pedidos y usuarios.
- Pantallas de reportes y sensores.
- Integración autenticada y validación contra el backend desplegado.
- Navegación y flujos de perfil/pedidos; conexión de `AppContainer` a la sesión real.

La interfaz actual se limita a la pantalla inicial. Los adaptadores HTTP se prueban con un servidor HTTPS local; aún no hay flujos de negocio conectados ni validación contra producción.

## Requisitos

- Android Studio compatible con Android Gradle Plugin 9.5.1.
- JDK 25 para Gradle. La compilación verificada usó JetBrains JDK 25.0.4.
- Android SDK 37 y Build Tools 36.0.0.
- `ANDROID_HOME` configurado o `local.properties` con `sdk.dir`. Este último es un archivo local y no se versiona.

El bytecode de Java/Kotlin se compila para Java 17. `gradle/gradle-daemon-jvm.properties` exige un JDK 25 instalado localmente; el wrapper no descarga ni aprovisiona una JVM.

## Compilar y probar

Desde `android/`:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

En Linux/macOS:

```bash
./gradlew assembleDebug testDebugUnitTest
```

Si el entorno restringe el daemon de Kotlin, se puede usar `-Pkotlin.compiler.execution.strategy=in-process`.

## Ejecutar

1. Abrir `android/` en Android Studio.
2. Configurar el Gradle JDK en 25.
3. Seleccionar un emulador o un dispositivo conectado.
4. Ejecutar la configuración `app`.

También se puede instalar desde consola con un dispositivo conectado:

```powershell
.\gradlew.bat installDebug
```

## Versiones

| Componente | Versión |
|---|---|
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin / Compose Compiler | 2.2.10 |
| Compose BOM | 2025.04.01 |
| Activity Compose | 1.10.1 |
| Lifecycle | 2.9.0 |
| Coroutines | 1.10.2 |
| JUnit | 4.13.2 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| Build Tools | 36.0.0 |
| Bytecode Java/Kotlin | 17 |
| JDK de Gradle verificado | 25.0.4 (JetBrains) |
| versionName / versionCode | 0.1.0 / 1 |

Las versiones se declaran en `gradle/libs.versions.toml` y en los archivos Gradle del proyecto.

## Integridad del Gradle Wrapper

El wrapper usa Gradle 9.6.0 desde el sitio oficial y valida la descarga mediante `distributionSha256Sum`:

- Distribución: `https://services.gradle.org/distributions/gradle-9.6.0-bin.zip`
- SHA-256: `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`

El JAR del wrapper es el componente de Gradle incluido en el repositorio; no se descargó ni sustituyó durante esta preparación.

## Verificación

Verificación anterior de la base de interfaz (no acredita la compilación Android de los últimos adaptadores HTTP):

- Compilación exitosa con Gradle.
- Tareas de pruebas ejecutadas sin errores.
- Instalación exitosa en emulador.
- Pantalla inicial funcional.
- Diálogo «Conocer la aplicación» funcional.

Pendiente:

- Validar la compilación y la ejecución desde un clon limpio, sin cachés ni configuraciones locales.
- Revisar visualmente los temas claro/oscuro y la barra del sistema en dispositivo.
- Implementar y verificar las funcionalidades previstas y la integración con el backend.

## Estructura

- `app/src/main/java/.../MainActivity.kt`: punto de entrada de la aplicación.
- `app/src/main/java/.../ui/home/`: pantalla de inicio, estado MVVM y pruebas.
- `app/src/main/java/.../ui/theme/`: tema de Compose.
- `gradle/libs.versions.toml`: catálogo de versiones.

## Infraestructura HTTP (F1-C02A)
core/network crea OkHttp/Retrofit con Gson estricto. ApiBaseUrl requiere HTTPS, sin credenciales/query/fragmento y con barra final. No hay URL de producción fija ni llamadas de negocio conectadas a la pantalla.
ApiRequestExecutor obtiene un token actual mediante AccessTokenProvider suspendido antes de invocar un servicio Retrofit con cabecera Authorization. No hay runBlocking en interceptores ni almacenamiento/renovación/cierre. Cliente bloquea solicitudes a otro origen y desactiva redirecciones HTTP/HTTPS y reintentos automáticos; no incluye logging de credenciales.
AppResult/AppError están en core/result. El ejecutor exige cuerpo para lecturas y usa executeEmpty para los DELETE con 204/205, devolviendo Unit. Un 204 de reporte sigue siendo RespuestaInvalida. Los DTO de respuesta declaran campos nullable y los mapeadores comprueban los obligatorios antes de crear dominio. Los adaptadores LocalDate ISO y BigDecimal no convierten por Double ni replican reglas de negocio.
Dependencias: Retrofit/converter-gson 2.11.0, OkHttp 4.12.0 y Gson 2.11.0; MockWebServer/okhttp-tls 4.12.0 solo para pruebas HTTPS con certificados efímeros de localhost. No se desactiva la validación TLS.
Validación Android en Windows: .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace. La ejecución completa del código nuevo puede requerir terminal sin las restricciones ZipFS del runner; el resultado JVM auxiliar no equivale a APK ni sincronización Android completos.

## Contratos y repositorios HTTP

- `domain/usuarios`: `UsuariosRepository`, `Perfil` y `DatosPerfil`. Solo perfil propio; eliminar la cuenta también elimina la identidad Auth en el servidor. No hay operaciones administrativas.
- `domain/pedidos`: `PedidosRepository`, `Pedido` (13 atributos) y `DatosPedido` (12 editables). No se envían identificador ni propietario en el JSON de escritura.
- `domain/reportes`: `ReportesRepository`, filtros tipados, `PaginaReporte`, `PedidoReporte` y `PerfilReporte`. Los reportes de usuarios tienen alcance propio (cero o un resultado).
- `data/remote`: APIs Retrofit, DTO con nombres JSON explícitos y mapeadores. Fechas ISO, decimales exactos, arrays vacíos y páginas sin totales.
- `data/repository`: adaptadores HTTP consumibles mediante interfaces, reemplazables por simulados.
- `di/AppContainer`: construye cliente, APIs y repositorios. Recibe una URL HTTPS del servidor terminada en `/` y un `AccessTokenProvider`; las rutas ya incluyen `api/v1`. No contiene URL productiva, credenciales ni gestión real de sesión.

Los filtros ausentes se omiten. Los presentes se codifican conservando texto, mayúsculas y acentos; las reglas de negocio y autorización se aplican en Spring Boot. Los parámetros enum se traducen explícitamente a sus valores REST.

El proveedor devuelve `null` para sesión ausente/inválida. Sus fallos temporales esperables se traducen a `TokenUnavailableException` (sin causa ni datos sensibles), que produce `ServicioNoDisponible`; un fallo de transporte declarado mediante `IOException` produce `ErrorConexion`. El adaptador de sesión futuro debe traducir los fallos conocidos de su SDK. La cancelación y los errores de programación se propagan. La conversión/mapeo inválidos producen `RespuestaInvalida`. No se captura indiscriminadamente `Exception` o `Throwable`, ni se cierra sesión automáticamente por 503.

Las escrituras no tienen reintentos automáticos. Además de desactivar los reintentos de conexión, el cliente retira `Retry-After` de respuestas 503 para impedir el seguimiento automático que OkHttp 4.12 puede efectuar con valor cero; el código 503 se conserva. Ante `ErrorConexion` o `RespuestaInvalida` después de escribir, el resultado puede ser incierto: la futura interfaz deberá reconciliar el estado antes de ofrecer un reintento. No interpretar el error como prueba de que el servidor no modificó datos.

Validación actual de los adaptadores: **66 pruebas JVM, cero fallos**, ejecutadas con un proyecto JVM auxiliar ignorado que compila directamente los archivos reales de `core`, `domain`, `data` y `di`. Incluyen MockWebServer con TLS local y un repositorio simulado; no acreditan RLS real, entrega de correo ni despliegue. Las pruebas están en `app/src/test/` y forman parte de `testDebugUnitTest` cuando el entorno Android puede ejecutarlo.

La validación Android del código actual está **pendiente**: el intento del runner falló durante la configuración del catálogo Gradle con `GeneratedClassCompilationException` / `AccessDeniedException` de ZipFS, antes de compilar fuentes Android. No se generó ni validó un APK nuevo. Para validarlo en una terminal local de Windows desde `android/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
```

Pendientes: validación Android completa, sesión real, flujos de interfaz y revisión de reglas Gson/R8 antes de habilitar ofuscación para publicación.
