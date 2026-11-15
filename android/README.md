# Gestor de Pedidos — infraestructura Auth

Proyecto Android Kotlin/Compose, un módulo app, con backend Spring Boot existente.
Este primer cambio incorpora Auth REST, sesión cifrada Keystore, renovación coordinada,
configuración pública y conexión JWT con los repositorios Spring. La interfaz de Auth
se incorpora en el siguiente cambio. No incluye pantallas CRUD, reportes, sensores o ubicación.

## Compilar
Requiere JDK 25, Gradle 9.6.0 mediante el wrapper, AGP 9.5.1, SDK 37 y Build Tools 36.0.0.
Bytecode Java/Kotlin 17; minSdk 26. Abrir android/ en Android Studio y elegir Gradle JDK 25.
Configurar el SDK mediante ANDROID_HOME o local.properties local, no versionado.
Desde android/: .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
Las pruebas instrumentadas se ejecutan con :app:connectedDebugAndroidTest en un dispositivo disponible.

## Configuración pública
En el Gradle User Home del desarrollador, fuera del repositorio, usar gradle.properties:
```properties
GESTOR_AUTH_URL=https://<PROYECTO>.supabase.co/auth/v1/
GESTOR_AUTH_PUBLIC_KEY=<CLAVE_PUBLICA_PUBLISHABLE_O_ANON>
GESTOR_API_URL=https://<SERVICIO>.onrender.com/
```
No usar service_role, claves secretas, JWT de usuario ni contraseñas.
Los valores son públicos y llegan a BuildConfig al recompilar; no se validan contra servicios
reales en las pruebas simuladas. HTTPS obligatorio, sin duplicar auth/v1 ni api/v1.

La sesión usa AES-GCM/Keystore y almacenamiento excluido de respaldos. Un refresh de resultado
incierto no se reutiliza. Los 401 permiten como máximo repetir una lectura con credencial
renovada; las escrituras nunca se repiten automáticamente. Spring conserva la autorización.
El APK Debug se genera en app/build/outputs/apk/debug/ y se publica solo tras revisión separada.
