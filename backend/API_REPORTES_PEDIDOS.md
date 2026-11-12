# Reportes REST de pedidos propios

Requieren el perfil `persistencia` y Bearer JWT ES256 validado por la seguridad
existente. Son listados filtrados, no agregados ni reportes de usuarios.

## Estado y fechas

`GET /api/v1/reportes/pedidos/estado-fechas`

| Parametro | Comportamiento |
| --- | --- |
| `estado` | Opcional, coincidencia exacta; no se inventa un catalogo de estados |
| `desde`, `hasta` | Opcionales e independientes, ISO `yyyy-MM-dd`, limites inclusivos |
| `fechaBase` | `fecha` por defecto; tambien `fecha_envio` o `fecha_entrega` |

Si se proporcionan ambos limites se exige `desde <= hasta`. Se permiten estado
solo, fechas solas, combinaciones y rangos abiertos. Sin estos filtros se obtienen
los pedidos propios paginados. Se rechazan fechas inexistentes, campos presentes
vacios y fechas base fuera de la lista cerrada.

Ejemplo sintetico:
`GET /api/v1/reportes/pedidos/estado-fechas?estado=Pendiente&desde=2026-01-01&hasta=2026-01-31&fechaBase=fecha_envio&pagina=0&tamano=20`

## Ubicacion

`GET /api/v1/reportes/pedidos/ubicacion`

Filtros opcionales `pais`, `departamento`, `ciudad`, con **al menos uno** presente
y no vacio. Se combinan con AND mediante coincidencias exactas, sensibles a
mayusculas y acentos; no se alteran los textos almacenados.

Ejemplo sintetico:
`GET /api/v1/reportes/pedidos/ubicacion?pais=Colombia&departamento=Santander&ciudad=Bucaramanga&pagina=0&tamano=1`

## Paginacion y respuesta

Ambos endpoints admiten `pagina` desde cero (predeterminado `0`) y `tamano`
entre `1` y `100` (predeterminado `20`). Orden fijo y estable por `id ASC`, sin
ordenamiento arbitrario. Se liga LIMIT `tamano + 1` y OFFSET como parametros;
la fila extra solo determina `haySiguiente`, nunca se devuelve. No se calculan
totales. El orden es estable, pero paginas HTTP separadas no comparten snapshot
si otros procesos insertan o eliminan pedidos entre solicitudes.

Respuesta `200`, ejemplo con datos ficticios:

```json
{
  "contenido": [{
    "id": 7,
    "fecha": "2026-01-10",
    "fechaEnvio": "2026-01-11",
    "fechaEntrega": "2026-01-12",
    "cliente": "Cliente ficticio",
    "proveedor": "Proveedor ficticio",
    "valor": 100.50,
    "estado": "Pendiente",
    "pais": "Colombia",
    "departamento": "Santander",
    "ciudad": "Bucaramanga",
    "nomenclaturaVivienda": "Direccion ficticia",
    "propina": 2.00
  }],
  "pagina": 0,
  "tamano": 1,
  "haySiguiente": false
}
```

Sin resultados: `200` con `contenido: []` y `haySiguiente: false`, conservando
la pagina y el tamano solicitados. Los errores de validacion son `400` con
`application/problem+json`; sin JWT valido, `401`.

## Seguridad y cobertura

Solo se aceptan los parametros documentados, una vez cada uno. `usuario_id`,
`usuarioId`, otros parametros y duplicados se rechazan con `400`. No se toman
identidades de cabeceras personalizadas. El UUID se obtiene del contexto JWT
mediante el puerto de identidad, no requiere el correo del perfil.

Todos los valores se ligan con `NamedParameterJdbcTemplate`. Los unicos nombres
SQL seleccionables proceden de un switch cerrado de las tres fechas reales.
Cada consulta incluye `usuario_id = :usuarioId`, se ejecuta obligatoriamente
dentro de `EjecutorRlsJdbc` y comparte su DataSource; RLS sigue como segunda
proteccion. La respuesta omite el UUID del propietario. Los casos de uso fallan
cerrados si un adaptador devuelve filas ajenas, incluso en la fila extra.

Las pruebas locales cubren contratos, paginacion, SQL parametrizado, enlace
obligatorio con RLS y HTTP con filtros/decoder reales y JWKS efimero local.
La suite real conjunta `ReportesRlsIT` registra 12 pruebas: 10 aprobadas y 2 de
paginacion de pedidos omitidas. Requieren al menos dos pedidos propios de A y,
para ubicacion, dos que compartan pais; esa cobertura multipagina sigue pendiente.
Ver [resultados y limitaciones de integracion](INTEGRACION_REPORTES_RLS.md#resultados-comprobados-al-cierre-de-fase-5).
Las pruebas HTTP completas de extremo a extremo contra PostgreSQL y las
operaciones reales de escritura INSERT, UPDATE y DELETE siguen pendientes.
