# Entrega final V9 — Gestor de Pedidos

**Universidad de Cartagena · Actividad 2 · Ingeniería de Software**

**Desarrollador:** Neider Lizcano

**Preparación documental:** 14 de noviembre de 2026

**Rama de preparación:** `feature/entrega-v9`

**Base:** `main`, commit `a8c0f26` (merge del PR #13).

Este documento distingue la inspección local de artefactos, las ejecuciones
automatizadas observadas y las pruebas reales declaradas por el responsable.
No implica una nueva publicación, instalación, prueba Android o conexión a producción.

## 1. Artefactos definitivos

| Archivo | Tipo | Tamaño exacto |
| --- | --- | ---: |
| [GestorPedidos_V9.aia](../app/GestorPedidos_V9.aia) | Fuente Kodular, contenedor ZIP | 412.909 bytes |
| [GestorPedidos_V9.apk](../app/GestorPedidos_V9.apk) | Aplicación Android instalable, contenedor ZIP/APK | 6.605.001 bytes |

SHA-256 recalculados mediante lectura de los archivos, sin modificarlos:

```text
GestorPedidos_V9.aia
ebe77c0a02ca26aac3805e00ef300348693588cb43fc09a2feb6e1d8ce0c34f2

GestorPedidos_V9.apk
c1b1ada2d92189472b9452a9453c6bd2172b08cb9deb557ab7717a0550aad872
```

Los hashes identifican estos archivos concretos; no son una certificación de
ausencia de vulnerabilidades. Tras cualquier reexportación deberán recalcularse.
V8 se conserva como antecedente; los candidatos intermedios no son la entrega.

### 1.1 Discrepancia con los hashes previos

La comprobación de esta revisión encontró que los archivos actuales **no coinciden**
con los hashes registrados antes de la incorporación de las capturas:

| Archivo | Hash previo | Hash actual |
| --- | --- | --- |
| `GestorPedidos_V9.aia` | `b6622f1421a474773cb4c91b024cd366bfc2e8dcafa3c66a0e790f551a571318` | `ebe77c0a02ca26aac3805e00ef300348693588cb43fc09a2feb6e1d8ce0c34f2` |
| `GestorPedidos_V9.apk` | `53a2efb60057a9b0960c133fe7f1c8921b2d57c521c4bd84556956da5ac58730` | `c1b1ada2d92189472b9452a9453c6bd2172b08cb9deb557ab7717a0550aad872` |

El AIA cambió de 413.080 a 412.909 bytes; el APK conserva 6.605.001 bytes. Las
fechas de modificación son 14/11/2026 4:15:45 y 4:15:54. No se modificaron durante
esta revisión. La estructura del AIA (20 entradas, 8 pantallas, 5.264 bloques,
mismas rutas) y la firma del APK (mismo certificado, v1/v2/v3 verificadas) son
coherentes, pero no se demostró equivalencia byte a byte con la exportación anterior.
El responsable confirmó que estos son los archivos definitivos; los cambios
corresponden a ajustes visuales.

## 2. Versión interna y requisitos Android

- **V9:** identificador académico de la entrega y nombre de los archivos finales.
- **`versionName=1.0`, `versionCode=1`:** metadatos internos actuales del AIA y APK.
- Proyecto interno Kodular: `GestorPedidos_FINAL_V2`.
- Package Android: `com.neiderlizcano.gestorpedidos`.
- Android mínimo: **6.0/API 23**. Target declarado: **API 36**.
- Se necesita Internet y disponibilidad de los servicios remotos; la ubicación
  requiere los permisos correspondientes si se utiliza esa funcionalidad.
- El APK no requiere Java/Maven en el teléfono. El AIA se importa y compila en Kodular Creator.

Renombrar los archivos como V9 no incrementa la versión de Android. V8 también
declara código 1 y comparte package; una futura publicación como actualización
debe planificar el incremento de `versionCode` y conservar la firma adecuada.
No se modificaron metadatos ni se reexportó el APK durante esta preparación
documental; la exportación actual es la entregada por el responsable.
V8 admitía API 21; V9 no debe presentarse como compatible con Android 5.

## 3. Integridad y correspondencia estructural

**Comprobaciones locales de solo lectura:**

| Comprobación | Resultado |
| --- | --- |
| AIA ZIP/CRC | Correcto; 20 entradas |
| APK ZIP/CRC | Correcto; 668 entradas y manifiesto Android presente |
| Entradas duplicadas o rutas inseguras de extracción | No detectadas en ambos contenedores |
| Fuente Kodular | Ocho SCM JSON y ocho BKY XML analizables; dos recursos PNG |
| Bloques | 5.264; sin referencias inexistentes a componentes/procedimientos ni eventos duplicados detectados |
| Compilación empaquetada | Dos DEX y recursos Android presentes; `debuggable=false` |
| Coherencia AIA/APK | Nombre interno de pantallas y configuración pública de integración coherentes |

Pantallas: `Screen1`, `Registro`, `Inicio`, `MiPerfil`, `Mis_pedidos`,
`NuevoPedido`, `EditarPedido` y `Reportes`. La recuperación se integra en el flujo
de autenticación/perfil; no hay una novena pantalla independiente de recuperación.

La inspección estática no demuestra que el APK sea una compilación reproducible
exacta del AIA. La confirmación de funcionamiento Android procede del usuario.

## 4. Verificación de firma

`apksigner.bat` no estaba en `PATH`, pero se localizó sin instalar herramientas en
**Android SDK Build Tools 36.0.0**:
`C:\Users\ASUS\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat`.
La comprobación fue de solo lectura y no modificó el APK:

```text
apksigner verify --verbose --print-certs app/GestorPedidos_V9.apk
```

Resultado: **`Verifies`**, código de salida **0**, sin errores.

| Esquema | Resultado |
| --- | --- |
| v1 (JAR signing) | Verificado |
| v2 (APK Signature Scheme v2) | Verificado |
| v3 (APK Signature Scheme v3) | Verificado |
| v3.1 | No verificado |
| v4 | No verificado |
| SourceStamp | No verificado |

- Un firmante; clave **RSA de 2048 bits**.
- Huella pública SHA-256 del certificado:
  `16d6b3d85f1277af568b7660b2c3d9366f9cd4562854d319b530fd1d1a35b3ae`.
- Advertencia: dos entradas `META-INF/services` de Kotlin no están protegidas por
  la firma JAR/v1. No impidió la verificación; v2/v3 protegen el APK completo en
  las versiones de Android que las admiten.
- La verificación acredita integridad y correspondencia con el certificado
  incluido; no acredita por sí sola la identidad externa del editor ni la ausencia
  de malware.
- `jarsigner -verify -strict` se usó antes como comprobación parcial: devolvió
  código 4 por cadena de certificado autofirmado no confiable para Java y ausencia
  de sello de tiempo. Ese resultado no contradice la verificación Android v1/v2/v3.

No se publican claves privadas, contraseñas ni material de firma. El SHA-256 del
certificado es información pública de identificación, no una clave.

## 5. Registro de pruebas y procedencia

### 5.1 Android V9 — declaración del usuario

El responsable comunicó que instaló/probó `GestorPedidos_V9.apk` en Android y que
**todas las funciones trabajan correctamente**. Se registra como aceptación
funcional global declarada, sin atribuir al agente la ejecución ni inventar
resultados individuales, modelo de dispositivo, versión Android o duración.

No se repitieron pruebas Android durante esta preparación. Se dispone de seis
capturas; dos se consideraron publicables y cuatro requieren saneamiento o
autorización por mostrar correo/nombre real o no coincidir con el contenido
declarado. Los datos del dispositivo (modelo, versión Android y fecha) no fueron
aportados y no se inventan.

### 5.2 Backend — ejecuciones locales observadas

- Suite completa seleccionada con `*Test,*Tests`, sin integración de BD real:
  **834 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
- Posteriormente se incorporó `@Qualifier("clienteEliminarCuenta")`; la selección
  relacionada de eliminación, JWT y clientes HTTP obtuvo **180 pruebas aprobadas,
  0 fallos y 0 errores**. No se volvió a ejecutar la suite completa después de ese ajuste.
- La compilación de cada prueba optativa real se verificó inicialmente sin las
  variables de entorno: BUILD SUCCESS y una prueba omitida en cada ejecución.

Los números anteriores corresponden a ejecuciones y coberturas distintas: no se
suman como casos independientes. Las pruebas locales emplearon dobles de prueba,
firmas efímeras y JWKS local, no usuarios ni credenciales reales.
No se acredita con ello un arranque conjunto completo de todos los componentes
con `persistencia`; las configuraciones HTTP se comprobaron aisladamente.

### 5.3 Postman y Supabase de pruebas — evidencia comunicada por el usuario

| Escenario | Resultado comunicado | Alcance acreditado |
| --- | --- | --- |
| Login, creación de perfil y eliminación de cuenta propia | Login correcto; DELETE `/api/v1/usuarios/me` con 204; ausencia en Auth/perfil; login posterior 400 `invalid_credentials` | Flujo de la cuenta ficticia, no una autorización para eliminar usuarios reales |
| JWT anterior antes de la corrección | GET de pedidos devolvía 200 `[]` tras eliminar la cuenta | Reproducción histórica del problema; no es el resultado esperado del filtro nuevo |
| GET directo `/auth/v1/user`, cuenta activa | 200 | Auth reconoce la cuenta |
| Mismo GET, cuenta eliminada y JWT aún vigente | 403, `error_code=user_not_found` | Supabase confirma que el usuario del JWT ya no existe |
| `CuentaActivaSupabaseRealIT` | BUILD SUCCESS, una prueba aprobada | Adaptador real acepta la identidad activa esperada |
| `VerificarCuentaActivaHttpIT` | BUILD SUCCESS, una prueba aprobada | Adaptador real rechaza la cuenta eliminada mediante `CuentaNoAutorizadaException` |

El filtro convierte ese rechazo en **401 de la API Spring**; se comprobó mediante
pruebas locales de la cadena. Las dos pruebas reales son del adaptador sin Spring
Boot/JDBC: **no equivalen a una prueba HTTP integral del filtro desplegado**.
No se adjuntan tokens, claves, correos, UUID de cuentas ni exportaciones con sesiones.

### 5.4 Estado comunicado de producción

El usuario confirmó que el PR #13 fue integrado en `main` y desplegado
correctamente en Render. La preparación local partió de `a8c0f26`, cuyo historial
contiene la corrección JWT y el ajuste de inyección explícita de ambos clientes.
No se consultaron nuevamente Render/Supabase ni se hicieron operaciones
productivas para elaborar este documento. Los documentos anteriores pueden
conservar pendientes históricos; esta sección distingue las confirmaciones posteriores.

## 6. Matriz de funcionalidades y evidencias

La columna Android resume la aceptación global comunicada, no una nueva prueba
individual instrumentada. Solo se enlazan las capturas aprobadas; las demás
permanecen fuera de la publicación hasta su saneamiento.

| Funcionalidad | Evidencia disponible | Captura o evidencia visual |
| --- | --- | --- |
| Registro e inicio de sesión | AIA y código inspeccionados; aceptación Android del usuario | Pendiente: captura sin correo ni contraseña |
| Sesión y cierre de sesión | Implementación y pruebas locales; aceptación Android | Pendiente: inicio/cierre sin datos privados |
| Recuperación por OTP | Ruta de verificación y actualización Auth en AIA; aceptación Android global | [05_01_recuperacion.jpeg](../screenshots/05_01_recuperacion.jpeg): diálogo OTP sin correo, código ni contraseña |
| Perfil: crear, consultar y actualizar | API propia y AIA; aceptación Android | Pendiente: perfil con datos ficticios; la captura 03 muestra datos reales y no se publica |
| Eliminación de cuenta propia | Pruebas locales y evidencia real comunicada del flujo autorizado | Pendiente: confirmación/resultado sanitizado, sin repetir una eliminación |
| CRUD de pedidos | Controladores, dominio y AIA; aceptación Android | Pendiente: alta/listado/edición con datos ficticios; la captura 02 muestra un nombre real y no se publica |
| Reporte nombre/username | Endpoint parametrizado y AIA; aceptación Android | Pendiente: filtro/resultado propio anonimizado |
| Reporte correo | Endpoint parametrizado y AIA; aceptación Android | Pendiente: filtro/resultado con correo oculto |
| Reporte estado/fechas | Endpoint paginado y AIA; aceptación Android | Pendiente: filtros y resultados ficticios |
| Reporte ubicación | Endpoint paginado y AIA; aceptación Android | [04_reportes.jpeg](../screenshots/04_reportes.jpeg): filtro por país y resultado de un pedido de prueba sin identidad directa |
| Ubicación | Componente y permisos presentes; aceptación Android global | Pendiente: asistencia de ubicación sin coordenadas/domicilios reales |
| Acelerómetro | Componente presente; aceptación Android global | Pendiente: diálogo de actualización del listado |
| Cuenta activa/eliminada | Postman y dos IT reales comunicados; cadena Spring local | Pendiente: resúmenes sanitizados sin headers, tokens ni datos personales |

### 6.1 Estado de las capturas

| Archivo | Dimensiones | Estado | Motivo |
| --- | --- | --- | --- |
| `01_inicio.jpeg` | 789×1600 | No publicable | Muestra un correo real |
| `02_mis_pedidos.jpeg` | 828×1600 | No publicable | Muestra un nombre real y un pedido |
| `03_nuevo_pedido.jpeg` | 776×1600 | No publicable | Muestra correo y nombre reales; el contenido es **Mi perfil**, no **Nuevo pedido** |
| `04_reportes.jpeg` | 796×1600 | Aprobada | Sin identidad directa; datos de un pedido de prueba |
| `05_recuperacion.jpeg` | 738×1600 | No publicable | Muestra un correo real |
| `05_01_recuperacion.jpeg` | 738×1600 | Aprobada | Sin correo, OTP, contraseña ni JWT |

Las seis se abrieron y decodificaron correctamente; no presentan metadatos EXIF.
Las no publicables no se modificaron.

### 6.2 Capturas aprobadas

| Captura | Contenido publicado | SHA-256 |
| --- | --- | --- |
| [04_reportes.jpeg](../screenshots/04_reportes.jpeg) | Reporte por ubicación: filtro por país y resultado de un pedido de prueba (ID, fecha, estado, ciudad y valor) | `75f962bacc260e34ef5a219b2091f43ea1af73dff4dac8f8606c2e3956b63f55` |
| [05_01_recuperacion.jpeg](../screenshots/05_01_recuperacion.jpeg) | Diálogo de recuperación OTP | `71e05ec0b1c95220b9c4a56efbe5fba41464909cc9dc01ddad3c1a4bc4a93d6e` |

Las capturas `01_inicio.jpeg`, `02_mis_pedidos.jpeg`, `03_nuevo_pedido.jpeg` y
`05_recuperacion.jpeg` no se publican: muestran correo institucional y/o nombre
real, y `03_nuevo_pedido.jpeg` corresponde visualmente a la pantalla **Mi perfil**,
no a **Nuevo pedido**. No se modificaron; requieren una versión saneada o una
autorización explícita del responsable. No se reproducen aquí los datos expuestos.

## 7. Seguridad y limitaciones conocidas

1. **Destino productivo:** los artefactos apuntan a Render/Supabase de producción.
   Las pruebas destructivas requieren otro entorno y autorización separada.
2. **Contenido estático:** no se detectaron patrones de claves privadas Supabase,
   JWT incrustados, claves privadas PEM, tokens GitHub ni URI de BD con contraseña.
   No aparecieron credenciales prellenadas ni identificadores de cuentas en los
   bloques examinados; el único correo encontrado es un placeholder de ejemplo
   (`correo@ejemplo.com`) en el campo de registro. Es una inspección por patrones,
   no garantía absoluta.
3. **Clave pública:** se encontró una publishable de Supabase, coherente en AIA/APK;
   su presencia no debe confundirse con la exposición de `service_role` o una clave secreta.
4. **Transporte:** los destinos encontrados son HTTPS, pero el manifiesto y la
   configuración de red permiten tráfico HTTP sin cifrar. No se afirma que las
   solicitudes actuales utilicen HTTP; la política permisiva es un límite conocido.
5. **Componentes exportados:** las ocho actividades Android son exportadas; su
   resistencia a aperturas directas no fue probada dinámicamente en esta revisión.
   El FileProvider observado no está exportado.
6. **Permisos:** ubicación precisa/aproximada, Internet, estado de red/Wi-Fi,
   lectura de almacenamiento y permisos adicionales de ubicación. Se declara
   `requestLegacyExternalStorage`; no garantiza acceso equivalente en Android moderno.
7. **Persistencia local:** los bloques TinyDB guardan estado e identidad visible,
   incluidos nombre/correo, no JWT/OTP/contraseñas. No se certifica cifrado ni
   comportamiento de backup del SDK Auth o de los datos locales.
8. **Cuenta activa:** no hay caché; cada solicitud protegida añade una consulta
   Auth y latencia. Caídas, límites de servicio o errores inesperados bloquean con
   503. La comprobación no cancela operaciones ya autorizadas/en curso ni constituye
   una lista de revocación universal de todos los JWT tras cerrar sesión.
9. **Eliminación:** física e irreversible mediante la API, con cascada. Storage,
   subidas concurrentes y fallos de transporte pueden requerir comprobaciones
   adicionales; un timeout no demuestra que el borrado se haya revertido.
10. **Alcance de evidencia:** no se afirma auditoría dinámica completa, análisis
    antimalware, verificación v3.1/v4 ni prueba HTTP real de cada escenario de
    seguridad. La firma v1/v2/v3 sí fue verificada con `apksigner`; las capturas
    publicadas son evidencia visual limitada y no sustituyen pruebas instrumentadas.
11. **Cobertura de firma v1:** dos entradas `META-INF/services` de Kotlin no están
    protegidas por la firma JAR/v1. En Android 6/API 23, donde v2/v3 no aplican,
    esas entradas podrían modificarse sin detección por v1. El resto del APK sí
    está cubierto; en Android 7+ v2 y en Android 9+ v3 protegen el conjunto. No se
    observó manipulación, pero la limitación debe considerarse.

## 8. Pendientes para completar la entrega documental

- Sanear y/o autorizar las capturas `01_inicio.jpeg`, `02_mis_pedidos.jpeg`,
  `03_nuevo_pedido.jpeg` y `05_recuperacion.jpeg`, o reemplazarlas por versiones
  sin correo/nombre real. Aportar modelo, versión Android y fecha de la prueba.
- Adjuntar resúmenes sanitizados de Postman y de los dos IT reales, si se dispone
  de ellos, distinguiéndolos de los informes locales con dobles de prueba.
- Mantener explícitas las limitaciones de v3.1/v4, metadatos y manifiesto;
  cualquier cambio del APK/AIA requiere autorización y nueva identificación por hash.
- Tras revisión y autorización, preparar solo `README.md`, este informe, ambos V9
  y las dos capturas aprobadas (`04_reportes.jpeg` y `05_01_recuperacion.jpeg`).
  No usar inclusiones globales de Git.
- Excluir `.env`, claves, contraseñas, certificados, tokens, datos privados,
  `AGENTS.md`, `opencode.json`, `.postman/`, `postman/`, `app/salida/` y las
  capturas no aprobadas hasta su saneamiento.
  El `.gitignore` raíz actual no asegura por sí solo todas estas exclusiones.

Las versiones anteriores no se eliminaron. Esta tarea no realizó commit, push,
merge, cambios de backend, publicación de GitHub ni despliegue.
