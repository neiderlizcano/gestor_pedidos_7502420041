# Cuenta activa en Supabase Auth

## Flujo y contrato

1. Spring Security valida ES256, firma, emisor, expiracion y claims como antes.
2. `CuentaActivaFiltro`, despues de `BearerTokenAuthenticationFilter`, invoca el
   puerto `VerificarCuentaActiva` antes de autorizar el acceso al controlador.
3. `VerificarCuentaActivaHttp` consulta `GET <issuer>/user` (`/auth/v1/user`) con
   el JWT actual y la clave publishable. El `id` devuelto debe coincidir con `sub`.
4. Solo un HTTP 200 JSON valido con la identidad esperada permite continuar.

| Resultado de Auth | Resultado de la API |
|---|---|
| 200 con el mismo `id` | Continua el procesamiento normal |
| 401 o 403 | 401, `WWW-Authenticate: Bearer` |
| 404 JSON con `code=user_not_found` | 401 |
| 200 con un `id` distinto | 401 |
| Timeout, conexion fallida, 429, 5xx, redireccion u otra respuesta inesperada | 503 controlado |
| JSON invalido, demasiado grande, sin `id` o tipo de contenido incorrecto | 503 controlado |

No hay cache positiva ni negativa, ni reintentos o redirecciones. Cada solicitud
protegida con JWT valido consulta Auth de nuevo. `GET /actuator/health` comparte
el mismo matcher publico en autorizacion y exclusion del filtro: no consulta Auth,
incluso si lleva un JWT valido. Los JWT expirados/invalidos se rechazan antes de
consultar la cuenta. No se agregan endpoints publicos.

El puerto solo recibe UUID; el adaptador obtiene el JWT del contexto validado.
No se consulta `public.usuarios`: una cuenta de Auth sin perfil puede crear su
primer perfil normalmente. Dominio, casos de uso de negocio, SQL y RLS no cambian.

## Configuracion necesaria antes de un eventual despliegue

- Se reutiliza `spring.security.oauth2.resourceserver.jwt.issuer-uri` /
  `SUPABASE_AUTH_ISSUER`, exigiendo HTTPS y ruta `/auth/v1` sin query ni fragmento.
- Configurar `SUPABASE_PUBLISHABLE_KEY` (mapeada a
  `seguridad.cuenta.publishable-key`). Tambien se admite la propiedad existente
  `cuenta.publishable-key` como respaldo si la variable no esta definida.
- Solo se admite el formato publishable, nunca una clave administrativa.
  Una clave ausente/invalida impide iniciar el adaptador; no hay modo permisivo.
- No se han leido ni cambiado archivos de secretos ni configuracion remota.

El transporte tiene limites de pool, espera de conexion, conexion, TLS y lectura.
El cuerpo JSON se limita a 64 KiB. Los errores no contienen cuerpos remotos ni
causas del cliente HTTP. Las cabeceras de credenciales se retiran del objeto de
solicitud al terminar. No se guardan tokens en BD, cache o logs. Se deshabilitan
los loggers de HTTP wire/headers; no habilitarlos ni introducir trazas de cabeceras.

## Pruebas locales

Desde `backend/`, con dependencias ya disponibles:

```powershell
mvn -o "-Dtest=*Test,*Tests" "-Dspring.profiles.active=" test
```

No ejecutar `verify` ni activar `db-integration` para esta comprobacion local.
Se usan claves ES256 efimeras, JWKS en loopback, transporte Auth simulado y
repositorios simulados/en memoria. Ninguna llamada de prueba va a Supabase o
PostgreSQL. Los tests `*IT` con BD real estan excluidos.

Cobertura agregada: existente, eliminado entre dos solicitudes con el mismo JWT
(sin cache), expirado, `id` inconsistente, Auth no disponible, timeout, respuestas
invalidas, configuracion insegura y cero consultas de cuenta para health.
Las regresiones de controladores de usuarios, pedidos y reportes usan el puerto
simulado para no acceder a Auth; conservan las firmas y filtros JWT reales.
Crear perfil comprueba la invocacion del puerto y que no se exige buscar perfil
previo. La cadena completa de seguridad usa el adaptador real con HTTP simulado.

Resultado local (2026-11-13): **834 pruebas, 0 fallos, 0 errores, 0 omitidas**.
`git diff --check` sin errores de formato.

## Limites y validacion pendiente

- Agrega una llamada de red por solicitud protegida y dependencia de disponibilidad
  de Auth; una caida o rate limit bloquea el acceso con 503, deliberadamente.
- Sin cache local, la siguiente solicitud se rechaza cuando Auth confirma la
  eliminacion. No puede cancelar operaciones ya autorizadas/en curso ni eliminar
  la carrera entre la comprobacion y una eliminacion concurrente; FK/RLS siguen
  siendo la ultima defensa para escrituras.
- No es una lista de revocacion general de JWT ni promete invalidar cada sesion
  cerrada; comprueba la cuenta segun la respuesta actual de Supabase Auth.
- Queda pendiente una prueba integrada en un entorno autorizado con cuenta
  ficticia: crear Auth/perfil, emitir JWT, eliminar cuenta y reutilizar ese JWT
  en GET/POST de pedidos y POST de perfil, esperando 401. No se ha realizado ni
  se han utilizado datos reales. No se ha desplegado esta implementacion.
