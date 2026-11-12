# API REST del perfil propio

Disponible con el perfil `persistencia`, utilizando los puertos y casos existentes.
Se conservan los filtros y el decoder JWT ES256 de la API de pedidos.

| Metodo y ruta | Resultado |
| --- | --- |
| `POST /api/v1/usuarios` | `201`, perfil y `Location: /api/v1/usuarios/me` |
| `GET /api/v1/usuarios/me` | `200`, perfil propio |
| `PUT /api/v1/usuarios/me` | `200`, perfil con `username` y `nombre` actualizados |
| `DELETE /api/v1/usuarios/me` | `204`, cuenta Auth propia eliminada y confirmada mediante Edge Function |

POST y PUT solo admiten `{"username":"usuario","nombre":"Nombre"}`. Ambos textos
son obligatorios y no pueden estar en blanco, igual que en el dominio. Cualquier
campo adicional (incluidos UUID y correo, incluso nulos) se rechaza con `400`.
No se admiten parametros de consulta. No existen listados globales ni rutas por UUID.
Las respuestas de perfil contienen exclusivamente `id`, `username`, `nombre` y `email` propios.

UUID y correo proceden del `sub` y del claim superior `email` del JWT validado,
respectivamente. No se usan cuerpo, cabeceras personalizadas ni `user_metadata`
para establecer identidad. Si el claim `email` falta, esta vacio o no es texto,
se responde `401` sin consultar persistencia. Esto tambien afecta a GET y PUT,
porque el puerto de identidad de perfiles exige UUID y correo en esos casos.
PUT conserva el UUID y el correo del perfil persistido; no sincroniza cambios de
correo de Auth. Estas operaciones de perfil no llaman a Supabase Auth.

DELETE es una operacion separada: solo exige el sub del JWT, sin email ni perfil,
sin cuerpo, Content-Type o parametros. Usa la funcion protegida delete-my-account
y Auth Admin; nunca recibe un UUID objetivo ni utiliza el adaptador JDBC antiguo.
Solo responde 204 tras confirmacion remota. Errores de autenticacion son 401,
errores remotos 502/503 y timeout sin confirmacion 504; 409 se reserva exclusivamente
para Storage identificado con certeza, no para mensajes genericos de base de datos.
Requiere la configuracion HTTPS documentada y una instalacion posterior autorizada;
**no esta desplegado**. Ver [diseno, configuracion y limites](../database/DELETE_CUENTA_PROPIA.md).

Errores: `400` por solicitud invalida, `401` sin identidad confiable, `404` si falta
el perfil propio y `409` si crear devuelve conflicto de UUID/correo. Los mensajes
son genericos, sin revelar perfiles ajenos. Se reutiliza el manejo comun
`application/problem+json` de pedidos, sin exponer causas JDBC ni datos recibidos.

Las pruebas MockMvc usan casos, adaptador de identidad y filtros reales, repositorio
simulado, firmas ES256 efimeras y JWKS local. Las pruebas HTTP completas de extremo
a extremo con PostgreSQL real y las operaciones reales INSERT, UPDATE y DELETE
siguen pendientes; estos tests locales no las sustituyen. Ver el
[estado de validacion de Fase 5](INTEGRACION_REPORTES_RLS.md#resultados-comprobados-al-cierre-de-fase-5).
