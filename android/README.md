# Gestor de Pedidos — Android nativo

Aplicación Android nativa del proyecto Gestor de Pedidos (Actividad 3, Universidad de Cartagena), escrita en Kotlin con Jetpack Compose. Es independiente de la aplicación Kodular del mismo proyecto y usa el identificador `com.neiderlizcano.gestorpedidos.nativo`.

## Estado actual

### Implementado
- Pantalla de inicio en Jetpack Compose.
- Estado de interfaz con MVVM y `StateFlow`, observado según el ciclo de vida.
- Diálogo «Conocer la aplicación».
- Tema base claro/oscuro.
- Pruebas unitarias de `HomeViewModel`.

### Previsto, no implementado
- Inicio de sesión y autenticación.
- CRUD de pedidos y usuarios.
- Reportes y sensores.
- Integración REST con el backend.
- Navegación, inyección de dependencias, perfil y módulos de pedidos.

La versión actual se limita a la pantalla inicial. Las funcionalidades previstas no están terminadas ni deben presentarse como completadas.

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

Verificado localmente:

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
