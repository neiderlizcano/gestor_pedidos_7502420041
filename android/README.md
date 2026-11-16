# Gestor de Pedidos — Android

**Universidad de Cartagena · Unidad 3 · Desarrollo de Aplicaciones Móviles**
**Ejercicio 17 — Pedidos · Desarrollador: Neider Lizcano**

Aplicación nativa para gestionar pedidos y el perfil propio de cada usuario.
Reutiliza el backend Spring Boot de Unidad 2, desplegado en Render, con PostgreSQL
y autenticación en Supabase. La entrega histórica Kodular se conserva separada.

## Tecnologías

| Área | Tecnologías verificadas |
| --- | --- |
| Interfaz | Kotlin 2.2.10, Jetpack Compose, Material 3, Compose BOM 2025.04.01 |
| Arquitectura | MVVM, ViewModel, StateFlow, corrutinas 1.10.2 y repositorios |
| Comunicación | Retrofit 2.11.0, OkHttp 4.12.0 y Gson 2.11.0 |
| Compilación | Android Studio, Gradle 9.6.0 (Kotlin DSL) y AGP 9.5.1 |
| Backend | Java 17, Spring Boot 4.1.2, Spring Security, JDBC y Maven |
| Datos y seguridad | Supabase PostgreSQL, RLS, Supabase Auth/JWT y Android Keystore |
| Pruebas | JUnit 4.13.2, MockWebServer, AndroidX Test y Espresso 3.7.0 |

## Funcionalidades principales

- Registro, login, recuperación por correo/OTP y cierre de sesión.
- Sesión cifrada, restauración y renovación JWT coordinada.
- **Mi perfil:** consultar, crear, editar nombre/username y eliminar la cuenta propia;
  correo de solo lectura. Eliminar la cuenta también elimina sus pedidos.
- **Pedidos:** listar, consultar, crear, editar y eliminar; calendarios, errores por campo
  y desplazamiento vertical con barra arrastrable.
- Cuatro reportes parametrizados, acelerómetro y ubicación manual.
- Borradores conservados al rotar y descartados al cerrar sesión o cambiar de cuenta.

Los pedidos mantienen los 13 atributos: `id`, `fecha`, `fechaEnvio`, `fechaEntrega`,
`cliente`, `proveedor`, `valor`, `estado`, `pais`, `departamento`, `ciudad`,
`nomenclaturaVivienda` y `propina`. El servidor asigna ID y propietario.
Se validan fechas ordenadas, valor positivo y propina no negativa mediante BigDecimal.

## Arquitectura MVVM y API REST

Flujo: **Compose → ViewModel/StateFlow → Repositorio → Retrofit/OkHttp → Spring Boot → PostgreSQL**.
Supabase Auth utiliza un cliente HTTPS separado. Spring conserva su arquitectura
hexagonal, las reglas de negocio y la autorización; Android no accede directamente a las tablas.
Las consultas se limitan al usuario del JWT. Las escrituras no se reintentan automáticamente.

Paquetes principales: `ui` (pantallas/ViewModels), `domain` (modelos/contratos),
`data` (Auth, DTO y repositorios), `core` (HTTP/sesión), `di`, `sensores` y `ubicacion`.
Contratos: [usuarios](../backend/API_USUARIOS.md) y [pedidos](../backend/API_PEDIDOS.md).

## Reportes parametrizados

Todos requieren JWT y consultan exclusivamente datos propios.

| Reporte | Ruta GET bajo `/api/v1/reportes` | Filtros |
| --- | --- | --- |
| Pedidos por estado/fechas | `/pedidos/estado-fechas` | `estado`, `desde`, `hasta`, `fechaBase` |
| Pedidos por ubicación | `/pedidos/ubicacion` | `pais`, `departamento`, `ciudad`; al menos uno |
| Perfil por nombre | `/usuarios/nombre-username` | `campo=nombre`, `termino`, `coincidencia` (exacta/prefijo) |
| Perfil por correo | `/usuarios/correo` | `correo` |

Los reportes de pedidos admiten `pagina >= 0` y `tamano` entre 1 y 100.
Las fechas usan `yyyy-MM-dd`; `fechaBase` admite `fecha`, `fecha_envio` o `fecha_entrega`.
La API de perfil también admite `campo=username`; la pantalla actual consulta por nombre.
Documentación: [reportes de pedidos](../backend/API_REPORTES_PEDIDOS.md) y [usuarios](../backend/API_REPORTES_USUARIOS.md).

## Acelerómetro y ubicación GPS

- **Acelerómetro:** muestra lecturas reales y disponibilidad; libera listeners al dejar de observar la pantalla.
- **Usar mi ubicación:** solo se activa mediante el botón al crear o editar pedidos.
  La geocodificación sugiere país, departamento, ciudad y dirección sin inventar datos;
  todos los campos siguen siendo editables manualmente.

Permisos: `INTERNET`, `ACCESS_COARSE_LOCATION` y `ACCESS_FINE_LOCATION`.
Se manejan permisos denegados, ubicación desactivada y resultados incompletos.
No hay seguimiento en segundo plano.

## Instalación del APK

[Descargar GestorPedidos_Unidad3.apk](apk/GestorPedidos_Unidad3.apk).
Versión **0.1.0**, código **1**, Debug con firma v2; **11.683.863 bytes**.
Requiere **Android 8.0/API 26 o superior**.

Descargar el archivo, comprobar su hash y permitir la instalación desde esa fuente.
Después, abrir Gestor de Pedidos y registrarse o iniciar sesión.
Para actualizar, conservar una firma compatible; no desinstalar una app con datos sin revisar el impacto.

**SHA-256:**

```text
2c1d3b0009f9297f3b7f54b12591ea416ea2910c10fb0fcb9886e4f1268e2507
```

Desde `android/`: `Get-FileHash .\apk\GestorPedidos_Unidad3.apk -Algorithm SHA256`.

## Instalación y compilación

```powershell
git clone --branch UND_3_Android https://github.com/neiderlizcano/gestor_pedidos_7502420041.git
Set-Location gestor_pedidos_7502420041\android
```

1. Abrir `android/` en Android Studio compatible con AGP 9.5.1.
2. Instalar SDK Platform 37 y seleccionar **JDK 25** como Gradle JDK; el bytecode es 17.
3. Configurar el SDK mediante Android Studio o un `local.properties` local.
4. Añadir estas propiedades a `%USERPROFILE%\.gradle\gradle.properties`
   —o al Gradle User Home configurado— y sustituir los marcadores:

```properties
GESTOR_AUTH_URL=https://<PROYECTO>.supabase.co/auth/v1/
GESTOR_AUTH_PUBLIC_KEY=<CLAVE_PUBLICA_PUBLISHABLE_O_ANON>
GESTOR_API_URL=https://<SERVICIO>.onrender.com/
```

Usar HTTPS y las barras finales indicadas. No añadir `api/v1/` a la URL Spring.
Los valores llegan a BuildConfig y forman parte del APK: nunca usar `service_role`,
contraseñas ni JWT personales. No versionar la configuración local.
Sincronizar Gradle, seleccionar el módulo `app` y ejecutar en un dispositivo autorizado.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

La última tarea requiere emulador o dispositivo. El APK compilado queda en
`app/build/outputs/apk/debug/app-debug.apk`; no confundirlo con el APK instrumental.

## API REST y Postman

La API usa HTTPS y `Authorization: Bearer <JWT>`; CRUD en `/api/v1/pedidos` y
`/api/v1/usuarios/me` (crear perfil: `POST /api/v1/usuarios`).
La evidencia histórica de Postman se distingue de las pruebas Android a continuación.

## Pruebas y evidencias

| Validación | Resultado y alcance |
| --- | --- |
| JVM, 15/11/2026 | 310 pruebas, cero fallos, errores u omisiones; XML locales |
| Instrumentadas completas anteriores | 77 aprobadas, comunicadas por el estudiante; XML completo no disponible en la salida actual |
| Regresión, 15/11/2026 | 14 de ubicación y 5 de formularios; 19 aprobadas, sin fallos, errores u omisiones |
| Android Lint, 15/11/2026 | 0 errores y 28 advertencias |
| APK final | Compilación satisfactoria, firma verificada e instalación/apertura en emulador |
| Samsung físico | Funcionamiento confirmado por el estudiante; validación manual |
| Postman, histórico de Unidad 2 | Login y eliminación de cuenta de prueba con 204 comunicados; no son pruebas Android |

Las 19 instrumentadas dirigidas no se suman a las 77 anteriores como casos distintos.
Los reportes JVM están en `app/build/test-results/testDebugUnitTest/`; los conectados,
en `app/build/outputs/androidTest-results/`; Lint, en `app/build/reports/`.
[Resultados históricos de Postman](../docs/ENTREGA_V9.md#53-postman-y-supabase-de-pruebas--evidencia-comunicada-por-el-usuario):
existen solicitudes locales de CRUD y reportes, pero no una colección JSON pública verificada.
Los contratos HTTP y las solicitudes preparadas no acreditan por sí solos ejecuciones manuales.
