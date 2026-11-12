# API REST de pedidos

Disponible con el perfil Spring `persistencia`, igual que los adaptadores JDBC.
Requiere un Bearer JWT ES256 valido; se conserva la configuracion de seguridad
existente. No se habilitan CORS general, sesiones ni rutas administrativas.

| Metodo y ruta | Resultado |
| --- | --- |
| `POST /api/v1/pedidos` | `201`, pedido y `Location` del recurso creado |
| `GET /api/v1/pedidos` | `200`, arreglo de pedidos propios (posiblemente vacio) |
| `GET /api/v1/pedidos/{id}` | `200`, pedido propio |
| `PUT /api/v1/pedidos/{id}` | `200`, pedido actualizado; reemplazo completo de datos editables |
| `DELETE /api/v1/pedidos/{id}` | `204`, sin cuerpo |

POST y PUT reciben JSON con `fecha`, `fechaEnvio`, `fechaEntrega` (ISO `yyyy-MM-dd`),
`cliente`, `proveedor`, `valor`, `estado`, `pais`, `departamento`, `ciudad`,
`nomenclaturaVivienda` y `propina`. Todos son obligatorios. Se aplican las reglas
reales del dominio (orden de fechas, texto no vacio, importes NUMERIC(12,2),
valor positivo y propina no negativa); no se define un catalogo nuevo de estados.

La respuesta contiene esos campos y `id`, nunca el UUID del propietario.
`id`, `usuarioId` y `usuario_id` en el cuerpo se ignoran; no se toman identidades
de parametros ni cabeceras personalizadas. El propietario lo obtienen los casos
de uso del contexto JWT validado. El controlador solo depende de puertos de entrada.

Sin autenticacion: `401`. Pedido inexistente o ajeno: el mismo `404` generico.
Datos o identificadores invalidos: `400`. Los errores de pedidos usan
`application/problem+json`, sin datos personales ni mensajes del driver.

Las pruebas MockMvc mantienen los filtros y el decoder reales, con claves ES256
efimeras y un JWKS local; simulan el repositorio, no abren JDBC ni usan Supabase.
Las pruebas HTTP completas de extremo a extremo con PostgreSQL real y las
operaciones reales INSERT, UPDATE y DELETE siguen pendientes; estos tests HTTP
no las sustituyen. Ver el
[estado de validacion de Fase 5](INTEGRACION_REPORTES_RLS.md#resultados-comprobados-al-cierre-de-fase-5).
