# Reportes REST del perfil propio

Disponibles con el perfil `persistencia` y Bearer JWT ES256 valido. Se reutilizan
la identidad de reportes, `PerfilRespuesta` y los errores REST comunes existentes.
Cada consulta considera **solo** `public.usuarios.id = sub` del JWT validado.
Nunca se consulta si otro usuario coincide con el filtro.

## Nombre o username

`GET /api/v1/reportes/usuarios/nombre-username`

Los tres parametros son obligatorios:

| Parametro | Valores |
| --- | --- |
| `campo` | Solo `nombre` o `username` |
| `termino` | Texto no vacio, maximo 200 caracteres, sin truncar ni normalizar |
| `coincidencia` | Solo `exacta` o `prefijo` |

La comparacion es sensible a mayusculas y acentos. En prefijo, `%`, `_`, `!` y
`\` del cliente son texto literal. SQL usa `LIKE :termino ESCAPE '!'`: se escapa
`!` como `!!`, `%` como `!%` y `_` como `!_`; `\` no es escape con esa clausula.
Solo se agrega un `%` final del servidor. Los valores se ligan, nunca se insertan
en el SQL; el nombre de columna proviene de un switch cerrado de un enum.

Ejemplos sinteticos (incluir el Bearer seguro sin publicarlo):

```text
GET /api/v1/reportes/usuarios/nombre-username?campo=nombre&termino=Persona&coincidencia=prefijo
GET /api/v1/reportes/usuarios/nombre-username?campo=username&termino=usuario_demo&coincidencia=exacta
GET /api/v1/reportes/usuarios/nombre-username?campo=nombre&termino=Ana%25_%21%5C&coincidencia=prefijo
```

El ultimo termino decodificado es `Ana%_!\`, no un patron arbitrario.

## Correo almacenado

`GET /api/v1/reportes/usuarios/correo?correo=persona%40example.invalid`

`correo` es obligatorio, no vacio, con formato de correo y maximo 254 caracteres.
La comparacion es **exacta** con `email` almacenado, no con el correo del JWT.
El parametro no es prueba de identidad: el propietario se determina solo por
`sub` validado. Este reporte no requiere un claim `email` en el JWT.

## Respuestas y seguridad

`200` devuelve un arreglo con cero o un perfil, reutilizando el DTO de usuarios:

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "username": "usuario_demo",
    "nombre": "Persona ficticia",
    "email": "persona@example.invalid"
  }
]
```

Sin perfil propio o sin coincidencia: `200` con `[]`, incluso si otro perfil
coincide. No se revelan perfiles ajenos. No hay listados globales, rutas por UUID
ni paginacion (la clave primaria limita el resultado propio a una fila).

Parametros faltantes, invalidos, largos, desconocidos o repetidos: `400` con
`application/problem+json`. Se rechazan `id`, `usuarioId` y `usuario_id` enviados
por el cliente. Sin JWT valido: `401`. Las cabeceras personalizadas no establecen
identidad. Los errores no incluyen valores recibidos ni causas del driver.

Todas las consultas son SELECT parametrizados con `NamedParameterJdbcTemplate`,
incluyen `WHERE id = :usuarioId` y pasan obligatoriamente por `EjecutorRlsJdbc`
con el mismo DataSource. Los casos fallan cerrados si reciben perfiles ajenos o
mas de una fila. No se cambian dominio, JDBC existente, roles ni politicas RLS.

Las pruebas locales usan consultas simuladas, casos y filtros reales, firmas
ES256 efimeras y JWKS local. La suite real conjunta `ReportesRlsIT` registra
12 pruebas: 10 aprobadas y 2 de paginacion de pedidos omitidas; no son pruebas
HTTP completas. Ver [resultados y limitaciones](INTEGRACION_REPORTES_RLS.md#resultados-comprobados-al-cierre-de-fase-5).
Las 18 pruebas reales (6 JDBC/RLS y 12 reportes) se omiten sin su bandera.
Siguen pendientes las pruebas HTTP completas de extremo a extremo contra
PostgreSQL y las operaciones reales de escritura INSERT, UPDATE y DELETE.
