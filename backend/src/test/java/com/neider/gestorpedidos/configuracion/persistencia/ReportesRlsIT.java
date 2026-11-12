package com.neider.gestorpedidos.configuracion.persistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.pedidos.dominio.PedidoInvalidoException;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorEstadoFechasCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorUbicacionCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorCorreoCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorNombreUsernameCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CampoPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CoincidenciaPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FechaBase;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginaPedidos;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc.ConsultasReportesJdbc;
import com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc.ConsultasReportesUsuariosJdbc;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.neider.gestorpedidos.usuarios.dominio.UsuarioInvalidoException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opentest4j.TestAbortedException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Integracion de los cuatro casos y adaptadores reales, no de HTTP ni de firmas JWT. Solo lectura. */
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class ReportesRlsIT extends SoporteJdbcRlsIT {

    private static final PaginacionReporte PRIMERA = new PaginacionReporte(0, 20);
    private final AtomicInteger consultasObservadas = new AtomicInteger();
    private ConsultasReportesJdbc consultasPedidos;
    private ConsultasReportesUsuariosJdbc consultasUsuarios;
    private ConsultarPedidosPorEstadoFechas estadoFechas;
    private ConsultarPedidosPorUbicacion ubicacion;
    private ConsultarPerfilPorNombreUsername nombreUsername;
    private ConsultarPerfilPorCorreo correo;

    @BeforeAll
    void prepararReportesConElEjecutorReal() {
        protegido(() -> {
            // Misma fuente y conexion ligada por Spring. La plantilla observa, no reemplaza el ejecutor real.
            var supervisado = new NamedParameterJdbcTemplate(pool) {
                @Override
                public <T> List<T> query(String sql, SqlParameterSource parametros, RowMapper<T> mapper) {
                    UUID id = new UsuarioAutenticado().usuarioActual();
                    exigir(id != null && TransactionSynchronizationManager.isActualTransactionActive()
                            && TransactionSynchronizationManager.hasResource(pool),
                            "El reporte intento consultar fuera de la transaccion RLS.");
                    exigir(sql.stripLeading().startsWith("SELECT ")
                            && (sql.contains("WHERE usuario_id = :usuarioId") || sql.contains("WHERE id = :usuarioId"))
                            && parametros.hasValue("usuarioId") && id.equals(parametros.getValue("usuarioId")),
                            "El reporte no conserva la consulta de lectura y su propietario parametrizado.");
                    contextoCorrecto(id);
                    consultasObservadas.incrementAndGet();
                    List<T> resultado = super.query(sql, parametros, mapper);
                    contextoCorrecto(id);
                    return resultado;
                }
            };
            consultasPedidos = new ConsultasReportesJdbc(supervisado, ejecutor);
            consultasUsuarios = new ConsultasReportesUsuariosJdbc(supervisado, ejecutor);
            IdentidadAutenticada identidad = new UsuarioAutenticado()::usuarioActual;
            estadoFechas = new ConsultarPedidosPorEstadoFechasCasoUso(consultasPedidos, identidad);
            ubicacion = new ConsultarPedidosPorUbicacionCasoUso(consultasPedidos, identidad);
            nombreUsername = new ConsultarPerfilPorNombreUsernameCasoUso(consultasUsuarios, identidad);
            correo = new ConsultarPerfilPorCorreoCasoUso(consultasUsuarios, identidad);
        });
    }

    @Test
    void sinIdentidadODiscordanteNingunoDeLosCuatroReportesConsulta() {
        protegido(() -> {
            UUID a = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID b = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var fechas = new FiltroEstadoFechas(null, null, null, FechaBase.FECHA);
            var lugar = new FiltroUbicacion("valor-sintetico", null, null);
            var nombre = new FiltroNombreUsername(CampoPerfil.NOMBRE, "valor-sintetico", CoincidenciaPerfil.EXACTA);
            var email = new FiltroCorreo("sintetico@example.invalid");
            int antes = consultasObservadas.get();
            SecurityContextHolder.clearContext();
            esperarRechazo(UsuarioNoAutenticadoException.class, () -> estadoFechas.ejecutar(fechas, PRIMERA));
            esperarRechazo(UsuarioNoAutenticadoException.class, () -> ubicacion.ejecutar(lugar, PRIMERA));
            esperarRechazo(UsuarioNoAutenticadoException.class, () -> nombreUsername.ejecutar(nombre));
            esperarRechazo(UsuarioNoAutenticadoException.class, () -> correo.ejecutar(email));
            conIdentidad(a, () -> {
                esperarRechazo(AccessDeniedException.class, () -> consultasPedidos.porEstadoFechas(b, fechas, PRIMERA));
                esperarRechazo(AccessDeniedException.class, () -> consultasPedidos.porUbicacion(b, lugar, PRIMERA));
                esperarRechazo(AccessDeniedException.class, () -> consultasUsuarios.porNombreUsername(b, nombre));
                esperarRechazo(AccessDeniedException.class, () -> consultasUsuarios.porCorreo(b, email));
                return true;
            });
            exigir(consultasObservadas.get() == antes, "Se ejecuto una consulta de reporte sin autorizacion.");
            exigir(baseLimpia(), "El rechazo no conservo el contexto JDBC limpio.");
        });
    }

    @Test
    void pedidosPorEstadoYRangosInclusivosEnLasTresFechasReales() {
        protegido(() -> {
            UUID a = usuarioA();
            Pedido muestra = primerPedido(a);
            for (FechaBase base : FechaBase.values()) {
                LocalDate dia = fecha(muestra, base);
                List<FiltroEstadoFechas> filtros = List.of(
                        new FiltroEstadoFechas(muestra.estado(), null, null, base),
                        new FiltroEstadoFechas(null, dia, dia, base),
                        new FiltroEstadoFechas(null, dia, null, base),
                        new FiltroEstadoFechas(null, null, dia, base),
                        new FiltroEstadoFechas(muestra.estado(), dia, dia, base));
                for (FiltroEstadoFechas filtro : filtros) {
                    PaginaPedidos pagina = reporte(a, () -> estadoFechas.ejecutar(filtro, PRIMERA));
                    comprobarPagina(pagina, a);
                    exigir(pagina.contenido().stream().anyMatch(p -> p.id().equals(muestra.id())),
                            "El reporte no encontro el pedido propio preexistente que satisface los filtros.");
                    exigir(pagina.contenido().stream().allMatch(p ->
                            (filtro.estado() == null || filtro.estado().equals(p.estado()))
                            && (filtro.desde() == null || !fecha(p, base).isBefore(filtro.desde()))
                            && (filtro.hasta() == null || !fecha(p, base).isAfter(filtro.hasta()))),
                            "El reporte devolvio pedidos fuera del estado o rango solicitado.");
                }
                // Rango valido que excluye la muestra conocida, sin necesitar otras filas ni fechas fijas.
                LocalDate otroDia = dia.withDayOfMonth(dia.getDayOfMonth() == 1 ? 2 : 1);
                var excluyente = dia.isBefore(otroDia)
                        ? new FiltroEstadoFechas(null, otroDia, null, base)
                        : new FiltroEstadoFechas(null, null, otroDia, base);
                PaginaPedidos fuera = reporte(a, () -> estadoFechas.ejecutar(excluyente, PRIMERA));
                comprobarPagina(fuera, a);
                exigir(fuera.contenido().stream().noneMatch(p -> p.id().equals(muestra.id()))
                        && fuera.contenido().stream().allMatch(p ->
                                (excluyente.desde() == null || !fecha(p, base).isBefore(excluyente.desde()))
                                && (excluyente.hasta() == null || !fecha(p, base).isAfter(excluyente.hasta()))),
                        "Un rango valido no excluyo el pedido propio conocido fuera de sus limites.");
            }
        });
    }

    @Test
    void pedidosPorUbicacionConFiltrosIndividualesYCombinados() {
        protegido(() -> {
            UUID a = usuarioA();
            Pedido muestra = primerPedido(a);
            for (int mascara = 1; mascara <= 7; mascara++) {
                var filtro = new FiltroUbicacion((mascara & 1) != 0 ? muestra.pais() : null,
                        (mascara & 2) != 0 ? muestra.departamento() : null, (mascara & 4) != 0 ? muestra.ciudad() : null);
                PaginaPedidos pagina = reporte(a, () -> ubicacion.ejecutar(filtro, PRIMERA));
                comprobarPagina(pagina, a);
                exigir(pagina.contenido().stream().anyMatch(p -> p.id().equals(muestra.id())),
                        "El reporte de ubicacion no encontro un pedido propio conocido.");
                exigir(pagina.contenido().stream().allMatch(p ->
                        (filtro.pais() == null || filtro.pais().equals(p.pais()))
                        && (filtro.departamento() == null || filtro.departamento().equals(p.departamento()))
                        && (filtro.ciudad() == null || filtro.ciudad().equals(p.ciudad()))),
                        "El reporte de ubicacion no aplico todos los filtros.");
            }
        });
    }

    @Test
    void pedidosPaginadosMantienenOrdenLimiteOffsetYHaySiguiente() {
        protegido(() -> {
            UUID a = usuarioA();
            List<Long> ids = idsPedidos(a, 3);
            if (ids.size() < 2) throw new TestAbortedException("Paginacion omitida: A necesita al menos dos pedidos preexistentes.");
            var filtro = new FiltroEstadoFechas(null, null, null, FechaBase.FECHA);
            PaginaPedidos primera = reporte(a, () -> estadoFechas.ejecutar(filtro, new PaginacionReporte(0, 1)));
            PaginaPedidos segunda = reporte(a, () -> estadoFechas.ejecutar(filtro, new PaginacionReporte(1, 1)));
            comprobarDosPaginas(primera, segunda, ids, a);
        });
    }

    @Test
    void pedidosPorUbicacionPaginadosCuandoHayDosCoincidenciasPropias() {
        protegido(() -> {
            UUID a = usuarioA();
            List<String> paises = leer(a, () -> ejecutor.ejecutar(a, () -> {
                contextoCorrecto(a);
                return jdbc.queryForList("""
                        SELECT pais FROM public.pedidos WHERE usuario_id = CAST(:id AS uuid)
                        GROUP BY pais HAVING count(*) >= 2 ORDER BY min(id) LIMIT 1
                        """, new MapSqlParameterSource("id", a.toString()), String.class);
            }));
            if (paises.isEmpty() || paises.get(0).isBlank()) {
                throw new TestAbortedException("Ubicacion paginada omitida: A necesita dos pedidos propios con el mismo pais apto.");
            }
            String pais = paises.get(0);
            List<Long> ids = leer(a, () -> ejecutor.ejecutar(a, () -> {
                contextoCorrecto(a);
                return jdbc.queryForList("""
                        SELECT id FROM public.pedidos WHERE usuario_id = CAST(:id AS uuid) AND pais = :pais
                        ORDER BY id ASC LIMIT 3
                        """, new MapSqlParameterSource("id", a.toString()).addValue("pais", pais), Long.class);
            }));
            if (ids.size() < 2) throw new TestAbortedException("Ubicacion paginada omitida: los datos de referencia cambiaron.");
            var filtro = new FiltroUbicacion(pais, null, null);
            PaginaPedidos primera = reporte(a, () -> ubicacion.ejecutar(filtro, new PaginacionReporte(0, 1)));
            PaginaPedidos segunda = reporte(a, () -> ubicacion.ejecutar(filtro, new PaginacionReporte(1, 1)));
            comprobarDosPaginas(primera, segunda, ids, a);
            exigir(primera.contenido().stream().allMatch(p -> pais.equals(p.pais()))
                    && segunda.contenido().stream().allMatch(p -> pais.equals(p.pais())),
                    "La paginacion de ubicacion no conservo el filtro.");
        });
    }

    @Test
    void pedidosSinCoincidenciasDevuelvenVacioYRangosInvalidosNoConsultan() {
        protegido(() -> {
            UUID a = usuarioA();
            String ausente = "__it_" + UUID.randomUUID();
            exigirSinCoincidencias(a, "estado", ausente);
            exigirSinCoincidencias(a, "pais", ausente);
            PaginaPedidos fechas = reporte(a, () -> estadoFechas.ejecutar(
                    new FiltroEstadoFechas(ausente, null, null, FechaBase.FECHA), new PaginacionReporte(1, 1)));
            PaginaPedidos lugar = reporte(a, () -> ubicacion.ejecutar(new FiltroUbicacion(ausente, null, null), PRIMERA));
            exigir(fechas.contenido().isEmpty() && !fechas.haySiguiente() && fechas.pagina() == 1
                    && fechas.tamano() == 1 && lugar.contenido().isEmpty() && !lugar.haySiguiente(),
                    "Un filtro sin coincidencias no produjo una pagina vacia correcta.");
            int antes = consultasObservadas.get();
            for (FechaBase base : FechaBase.values()) {
                esperarRechazo(ConsultaReporteInvalidaException.class, () -> estadoFechas.ejecutar(
                        new FiltroEstadoFechas(null, LocalDate.of(2000, 1, 2), LocalDate.of(2000, 1, 1), base), PRIMERA));
            }
            exigir(consultasObservadas.get() == antes, "Un rango invalido ejecuto una consulta.");
        });
    }

    @Test
    void perfilPorNombreExactoYPrefijoDerivadosDelPerfilPropio() {
        protegido(() -> comprobarNombre(CampoPerfil.NOMBRE));
    }

    @Test
    void perfilPorUsernameExactoYPrefijoDerivadosDelPerfilPropio() {
        protegido(() -> comprobarNombre(CampoPerfil.USERNAME));
    }

    @Test
    void perfilesTratanComodinesYEscapesComoTextoLiteral() {
        protegido(() -> {
            UUID a = usuarioA();
            Usuario propio = perfilRequerido(a);
            for (CampoPerfil campo : CampoPerfil.values()) {
                String almacenado = valor(propio, campo);
                for (String literal : List.of("%", "_", "!", "\\")) {
                    for (CoincidenciaPerfil modo : CoincidenciaPerfil.values()) {
                        var filtro = new FiltroNombreUsername(campo, literal, modo);
                        List<Usuario> filas = reporte(a, () -> nombreUsername.ejecutar(filtro));
                        comprobarPerfiles(filas, a);
                        boolean coincide = modo == CoincidenciaPerfil.EXACTA
                                ? almacenado.equals(literal) : almacenado.startsWith(literal);
                        exigir((filas.size() == 1) == coincide, "El reporte interpreto texto literal como comodines o escapes SQL.");
                    }
                }
            }
        });
    }

    @Test
    void correoExactoAlmacenadoYFiltroSinCoincidencia() {
        protegido(() -> {
            UUID a = usuarioA();
            Usuario propio = perfilRequerido(a);
            exigirCorreoApto(propio);
            List<Usuario> positivo = reporte(a, () -> correo.ejecutar(new FiltroCorreo(propio.email())));
            comprobarPerfiles(positivo, a);
            exigir(positivo.size() == 1 && positivo.get(0).email().equals(propio.email()),
                    "El reporte no encontro el correo propio almacenado.");
            String ausente = UUID.randomUUID() + "@it.invalid";
            exigir(!ausente.equals(propio.email()), "El filtro sintetico coincide con el correo propio.");
            List<Usuario> vacio = reporte(a, () -> correo.ejecutar(new FiltroCorreo(ausente)));
            exigir(vacio.isEmpty(), "Un correo distinto del propio expuso un perfil.");
        });
    }

    @Test
    void aislamientoABPedidosNoExigePedidosPropiosDeB() {
        protegido(() -> {
            UUID[] ids = usuariosAB();
            UUID a = ids[0];
            UUID b = ids[1];
            perfilRequerido(a);
            perfilRequerido(b);
            Pedido conocido = primerPedido(a);
            var fechas = new FiltroEstadoFechas(conocido.estado(), conocido.fecha(), conocido.fecha(), FechaBase.FECHA);
            var lugar = new FiltroUbicacion(conocido.pais(), conocido.departamento(), conocido.ciudad());
            PaginaPedidos positivo = reporte(a, () -> estadoFechas.ejecutar(fechas, PRIMERA));
            exigir(positivo.contenido().stream().anyMatch(p -> p.id().equals(conocido.id())),
                    "A/B requiere acceso positivo al pedido propio conocido de A.");
            PaginaPedidos bajoB = reporte(b, () -> estadoFechas.ejecutar(fechas, PRIMERA));
            PaginaPedidos ubicacionB = reporte(b, () -> ubicacion.ejecutar(lugar, PRIMERA));
            comprobarPagina(bajoB, b);
            comprobarPagina(ubicacionB, b);
            exigir(bajoB.contenido().stream().noneMatch(p -> p.id().equals(conocido.id()))
                    && ubicacionB.contenido().stream().noneMatch(p -> p.id().equals(conocido.id())),
                    "Los reportes permitieron acceso cruzado al pedido conocido de A.");
            boolean ocultoPorRls = leer(b, () -> ejecutor.ejecutar(b, () -> {
                contextoCorrecto(b);
                return Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT NOT EXISTS (SELECT 1 FROM public.pedidos WHERE id = :pedido)",
                        new MapSqlParameterSource("pedido", conocido.id()), Boolean.class));
            }));
            exigir(ocultoPorRls, "RLS no oculto bajo B el pedido identificado previamente bajo A.");
        });
    }

    @Test
    void aislamientoABUsuariosConAccesoPositivoYFiltrosCruzados() {
        protegido(() -> {
            UUID[] ids = usuariosAB();
            UUID a = ids[0];
            UUID b = ids[1];
            Usuario perfilA = perfilRequerido(a);
            Usuario perfilB = perfilRequerido(b);
            exigirCorreoApto(perfilA);
            exigirCorreoApto(perfilB);
            if (perfilA.email().equals(perfilB.email())) {
                throw new TestAbortedException("A/B de correo requiere correos propios distintos.");
            }
            for (CampoPerfil campo : CampoPerfil.values()) {
                exigirNombreApto(valor(perfilA, campo));
                exigirNombreApto(valor(perfilB, campo));
            }
            comprobarDireccionUsuarios(perfilA, perfilB);
            comprobarDireccionUsuarios(perfilB, perfilA);
        });
    }

    private void comprobarDireccionUsuarios(Usuario propio, Usuario ajeno) {
        UUID id = propio.id();
        List<Usuario> positivo = reporte(id, () -> correo.ejecutar(new FiltroCorreo(propio.email())));
        comprobarPerfiles(positivo, id);
        exigir(positivo.size() == 1, "A/B requiere acceso positivo al perfil propio por correo.");
        exigir(reporte(id, () -> correo.ejecutar(new FiltroCorreo(ajeno.email()))).isEmpty(),
                "El reporte por correo expuso un perfil ajeno conocido.");
        for (CampoPerfil campo : CampoPerfil.values()) {
            for (CoincidenciaPerfil modo : CoincidenciaPerfil.values()) {
                String termino = valor(ajeno, campo);
                List<Usuario> filas = reporte(id, () -> nombreUsername.ejecutar(new FiltroNombreUsername(campo, termino, modo)));
                comprobarPerfiles(filas, id);
                boolean coincidePropio = modo == CoincidenciaPerfil.EXACTA
                        ? valor(propio, campo).equals(termino) : valor(propio, campo).startsWith(termino);
                exigir((filas.size() == 1) == coincidePropio,
                        "El reporte cruzado por nombre no conserva la coincidencia del perfil propio.");
            }
        }
        boolean ocultoPorRls = leer(id, () -> ejecutor.ejecutar(id, () -> {
            contextoCorrecto(id);
            return verdadero("SELECT NOT EXISTS (SELECT 1 FROM public.usuarios WHERE id = CAST(:id AS uuid))", ajeno.id());
        }));
        exigir(ocultoPorRls, "RLS permitio consultar el perfil ajeno por UUID.");
    }

    private void comprobarNombre(CampoPerfil campo) {
        UUID a = usuarioA();
        Usuario propio = perfilRequerido(a);
        String almacenado = valor(propio, campo);
        exigirNombreApto(almacenado);
        int fin = 0;
        do {
            fin = almacenado.offsetByCodePoints(fin, 1);
        } while (almacenado.substring(0, fin).isBlank() && fin < almacenado.length());
        String prefijo = almacenado.substring(0, fin);
        for (CoincidenciaPerfil modo : CoincidenciaPerfil.values()) {
            String termino = modo == CoincidenciaPerfil.EXACTA ? almacenado : prefijo;
            List<Usuario> filas = reporte(a, () -> nombreUsername.ejecutar(new FiltroNombreUsername(campo, termino, modo)));
            comprobarPerfiles(filas, a);
            exigir(filas.size() == 1 && valor(filas.get(0), campo).equals(almacenado),
                    "El reporte no encontro el nombre o username propio mediante el filtro derivado.");
        }
    }

    private List<Long> idsPedidos(UUID id, int limite) {
        return leer(id, () -> ejecutor.ejecutar(id, () -> {
            contextoCorrecto(id);
            return jdbc.queryForList("""
                    SELECT id FROM public.pedidos WHERE usuario_id = CAST(:id AS uuid)
                    ORDER BY id ASC LIMIT :limite
                    """, new MapSqlParameterSource("id", id.toString()).addValue("limite", limite), Long.class);
        }));
    }

    private Pedido primerPedido(UUID id) {
        List<Long> ids = idsPedidos(id, 1);
        if (ids.isEmpty()) throw new TestAbortedException("Caso omitido: A necesita un pedido propio preexistente.");
        try {
            return leer(id, () -> pedidos.buscarPorIdYPropietario(ids.get(0), id))
                    .orElseThrow(() -> new TestAbortedException("Caso omitido: el pedido de referencia ya no esta disponible."));
        } catch (PedidoInvalidoException datosNoAptos) {
            throw new TestAbortedException("Caso omitido: el pedido preexistente no cumple el dominio actual.");
        }
    }

    private Usuario perfilRequerido(UUID id) {
        try {
            return leer(id, () -> usuarios.buscarPorId(id))
                    .orElseThrow(() -> new TestAbortedException("Caso omitido: falta un perfil propio preexistente visible."));
        } catch (UsuarioInvalidoException datosNoAptos) {
            throw new TestAbortedException("Caso omitido: el perfil preexistente no cumple el dominio actual.");
        }
    }

    private void exigirSinCoincidencias(UUID id, String campo, String texto) {
        String sql = switch (campo) {
            case "estado" -> "SELECT NOT EXISTS (SELECT 1 FROM public.pedidos WHERE usuario_id = CAST(:id AS uuid) AND estado = :valor)";
            case "pais" -> "SELECT NOT EXISTS (SELECT 1 FROM public.pedidos WHERE usuario_id = CAST(:id AS uuid) AND pais = :valor)";
            default -> throw new IllegalArgumentException("Campo de referencia no permitido.");
        };
        boolean ausente = leer(id, () -> ejecutor.ejecutar(id, () -> {
            contextoCorrecto(id);
            return Boolean.TRUE.equals(jdbc.queryForObject(sql,
                    new MapSqlParameterSource("id", id.toString()).addValue("valor", texto), Boolean.class));
        }));
        if (!ausente) throw new TestAbortedException("Caso vacio omitido: el filtro sintetico coincide con datos propios.");
    }

    private <T> T leer(UUID id, Supplier<T> operacion) {
        T resultado = conIdentidad(id, operacion);
        exigir(baseLimpia(), "Persisten identidad o claims tras una lectura de integracion.");
        return resultado;
    }

    private <T> T reporte(UUID id, Supplier<T> operacion) {
        int antes = consultasObservadas.get();
        T resultado = leer(id, operacion);
        exigir(consultasObservadas.get() > antes, "El caso de uso no ejecuto el adaptador JDBC observado.");
        return resultado;
    }

    private static UUID usuarioA() {
        String entrada = System.getenv("DB_TEST_USER_A");
        if (entrada == null || entrada.isBlank()) throw new TestAbortedException("Caso omitido: configurar DB_TEST_USER_A.");
        return uuidExterno(entrada);
    }

    private static UUID[] usuariosAB() {
        String entradaA = System.getenv("DB_TEST_USER_A");
        String entradaB = System.getenv("DB_TEST_USER_B");
        if (entradaA == null || entradaA.isBlank() || entradaB == null || entradaB.isBlank()) {
            throw new TestAbortedException("A/B de reportes omitida: proporcionar ambos UUID externos.");
        }
        UUID a = uuidExterno(entradaA);
        UUID b = uuidExterno(entradaB);
        exigir(!a.equals(b), "A/B requiere UUID distintos.");
        return new UUID[] {a, b};
    }

    private static void comprobarPagina(PaginaPedidos pagina, UUID propietario) {
        exigir(pagina.pagina() >= 0 && pagina.tamano() >= 1 && pagina.tamano() <= PaginacionReporte.TAMANO_MAXIMO
                && pagina.contenido().size() <= pagina.tamano()
                && pagina.contenido().stream().allMatch(p -> p.perteneceA(propietario))
                && IntStream.range(1, pagina.contenido().size()).allMatch(i ->
                        pagina.contenido().get(i - 1).id().compareTo(pagina.contenido().get(i).id()) < 0),
                "La pagina no conserva limite, orden estable o aislamiento de propietario.");
    }

    private static void comprobarDosPaginas(PaginaPedidos primera, PaginaPedidos segunda, List<Long> ids, UUID propietario) {
        comprobarPagina(primera, propietario);
        comprobarPagina(segunda, propietario);
        exigir(primera.pagina() == 0 && segunda.pagina() == 1 && primera.tamano() == 1 && segunda.tamano() == 1
                && primera.contenido().size() == 1 && segunda.contenido().size() == 1
                && primera.contenido().get(0).id().equals(ids.get(0))
                && segunda.contenido().get(0).id().equals(ids.get(1))
                && primera.haySiguiente() && segunda.haySiguiente() == (ids.size() > 2),
                "La paginacion no conserva limite, offset, orden o indicador de siguiente pagina.");
    }

    private static void comprobarPerfiles(List<Usuario> filas, UUID propietario) {
        exigir(filas.size() <= 1 && filas.stream().allMatch(p -> p.id().equals(propietario)),
                "El reporte expuso un perfil ajeno o mas de una fila.");
    }

    private static void exigirNombreApto(String valor) {
        if (valor.length() > FiltroNombreUsername.MAX_TERMINO) {
            throw new TestAbortedException("Caso omitido: el texto preexistente supera el limite del filtro.");
        }
    }

    private static void exigirCorreoApto(Usuario perfil) {
        if (perfil.email().length() > FiltroCorreo.MAX_CORREO) {
            throw new TestAbortedException("Caso omitido: el correo preexistente supera el limite del filtro.");
        }
    }

    private static String valor(Usuario perfil, CampoPerfil campo) {
        return campo == CampoPerfil.NOMBRE ? perfil.nombre() : perfil.username();
    }

    private static LocalDate fecha(Pedido pedido, FechaBase base) {
        return switch (base) {
            case FECHA -> pedido.fecha();
            case FECHA_ENVIO -> pedido.fechaEnvio();
            case FECHA_ENTREGA -> pedido.fechaEntrega();
        };
    }
}
