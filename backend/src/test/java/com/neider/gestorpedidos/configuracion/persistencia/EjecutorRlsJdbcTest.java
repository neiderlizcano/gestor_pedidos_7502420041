package com.neider.gestorpedidos.configuracion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc.EstadoBase;
import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc.EstadoRls;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.pedidos.infraestructura.salida.jdbc.RepositorioPedidosJdbc;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc.RepositorioUsuariosJdbc;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Pruebas del protocolo, no una demostracion del aislamiento real de PostgreSQL. */
class EjecutorRlsJdbcTest {

    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
    private final HikariDataSource dataSource = mock(HikariDataSource.class);
    private final HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    private final JdbcTransactionManager gestor = mock(JdbcTransactionManager.class);
    private final NamedParameterJdbcTemplate consultas = mock(NamedParameterJdbcTemplate.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SimpleTransactionStatus estado = new SimpleTransactionStatus(true);
    private final Negocio negocio = mock(Negocio.class);
    private EjecutorRlsJdbc ejecutor;

    @BeforeEach
    void preparar() {
        when(gestor.getDataSource()).thenReturn(dataSource);
        when(consultas.getJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.getDataSource()).thenReturn(dataSource);
        when(gestor.getTransaction(any())).thenReturn(estado);
        when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
        when(usuario.usuarioActual()).thenReturn(A);
        ejecutor = new EjecutorRlsJdbc(usuario, gestor, consultas);
        clearInvocations(gestor, consultas, jdbc, dataSource);
    }

    @AfterEach
    void limpiarTransaccionDePrueba() {
        TransactionSynchronizationManager.unbindResourceIfPossible(dataSource);
        TransactionSynchronizationManager.clear();
    }

    @Test
    void ordenTransaccionRolClaimsVerificacionNegocioYCommit() {
        contextoValido(A);
        when(negocio.get()).thenReturn("ok");

        assertThat(ejecutor.ejecutar(A, negocio)).isEqualTo("ok");

        var orden = inOrder(usuario, gestor, jdbc, negocio);
        orden.verify(usuario).usuarioActual();
        orden.verify(gestor).getTransaction(any());
        orden.verify(jdbc).queryForObject(eq(EjecutorRlsJdbc.ESTADO_BASE), mapperBase());
        orden.verify(jdbc).execute("SET LOCAL ROLE authenticated");
        orden.verify(jdbc).queryForObject(EjecutorRlsJdbc.SUB_LOCAL, String.class, A.toString());
        orden.verify(jdbc).queryForObject(EjecutorRlsJdbc.CLAIMS_LOCAL, String.class, claims(A));
        orden.verify(jdbc).queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS), mapperRls());
        orden.verify(negocio).get();
        orden.verify(gestor).commit(estado);
        verify(gestor, never()).rollback(any());
        verifyNoInteractions(pool);
    }

    @Test
    void sinJwtNoAbreTransaccionNiEjecutaSql() {
        when(usuario.usuarioActual()).thenReturn(null);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(gestor, jdbc, negocio, pool);
    }

    @Test
    void noUsaElPropietarioRecibidoComoIdentidad() {
        assertThatThrownBy(() -> ejecutor.ejecutar(B, negocio)).isInstanceOf(AccessDeniedException.class)
                .hasMessageNotContaining(A.toString()).hasMessageNotContaining(B.toString());
        verifyNoInteractions(gestor, jdbc, negocio, pool);
    }

    @Test
    void propietarioNuloNoPermiteOmitirLaComprobacion() {
        assertThatThrownBy(() -> ejecutor.ejecutar(null, negocio)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(gestor, jdbc, negocio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"activa", "sincronizacion", "conexion"})
    void rechazaContextosTransaccionalesExternosSinModificarlos(String tipo) {
        switch (tipo) {
            case "activa" -> TransactionSynchronizationManager.setActualTransactionActive(true);
            case "sincronizacion" -> TransactionSynchronizationManager.initSynchronization();
            case "conexion" -> TransactionSynchronizationManager.bindResource(dataSource, new Object());
            default -> throw new IllegalArgumentException();
        }

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(gestor, jdbc, negocio, pool);
    }

    @Test
    void exigeQueElGestorInicieUnaTransaccionNueva() {
        var externa = new SimpleTransactionStatus(false);
        when(gestor.getTransaction(any())).thenReturn(externa);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isInstanceOf(IllegalStateException.class);

        verify(gestor).rollback(externa);
        verifyNoInteractions(jdbc, negocio);
    }

    static Stream<Arguments> basesInseguras() {
        return Stream.of(
                arguments("rol cambiado", new EstadoBase("authenticated", "app_jdbc", "authenticated",
                        null, null, null, "on", true)),
                arguments("rol persistente", new EstadoBase("app_jdbc", "app_jdbc", "app_jdbc",
                        null, null, null, "on", true)),
                arguments("sub residual", new EstadoBase("app_jdbc", "app_jdbc", "none",
                        B.toString(), null, null, "on", true)),
                arguments("claims residuales", new EstadoBase("app_jdbc", "app_jdbc", "none",
                        null, claims(B), null, "on", true)),
                arguments("rol claim residual", new EstadoBase("app_jdbc", "app_jdbc", "none",
                        null, null, "authenticated", "on", true)),
                arguments("claims vacios no canonicos", new EstadoBase("app_jdbc", "app_jdbc", "none",
                        null, "{}", null, "on", true)),
                arguments("rol privilegiado", new EstadoBase("app_jdbc", "app_jdbc", "none",
                        null, null, null, "on", false)),
                arguments("rls apagado", new EstadoBase("app_jdbc", "app_jdbc", "none",
                        null, null, null, "off", true)),
                arguments("login no permitido", new EstadoBase("service_role", "service_role", "none",
                        null, null, null, "on", true)),
                arguments("sin estado", null));
    }

    @ParameterizedTest(name = "rechaza base: {0}")
    @MethodSource("basesInseguras")
    void rechazaEstadoResidualOPermisosInseguros(String escenario, EstadoBase base) {
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_BASE), mapperBase())).thenReturn(base);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isInstanceOf(IllegalStateException.class);

        verify(jdbc, never()).execute(anyString());
        comprobarRollbackSinNegocio();
    }

    @Test
    void errorAlCambiarDeRolImpideEstablecerClaimsOConsultarTablas() {
        baseLimpia();
        var error = new DataAccessResourceFailureException("Fallo sintetico de rol");
        doThrow(error).when(jdbc).execute(EjecutorRlsJdbc.ROL_LOCAL);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isSameAs(error);

        verify(jdbc, never()).queryForObject(eq(EjecutorRlsJdbc.SUB_LOCAL), eq(String.class), anyString());
        comprobarRollbackSinNegocio();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "claims"})
    void noContinuaSiSetConfigNoConfirmaElValor(String campo) {
        contextoValido(A);
        String sql = "sub".equals(campo) ? EjecutorRlsJdbc.SUB_LOCAL : EjecutorRlsJdbc.CLAIMS_LOCAL;
        when(jdbc.queryForObject(eq(sql), eq(String.class), anyString())).thenReturn("no-establecido");

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isInstanceOf(IllegalStateException.class);

        verify(jdbc, never()).queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS), mapperRls());
        comprobarRollbackSinNegocio();
    }

    static Stream<Arguments> contextosInvalidos() {
        return Stream.of(
                arguments("rol incorrecto", new EstadoRls("app_jdbc", A, "on", true)),
                arguments("auth.uid ajeno", new EstadoRls("authenticated", B, "on", true)),
                arguments("auth.uid ausente", new EstadoRls("authenticated", null, "on", true)),
                arguments("row_security apagado", new EstadoRls("authenticated", A, "off", true)),
                arguments("tablas sin proteccion", new EstadoRls("authenticated", A, "on", false)),
                arguments("sin estado", null));
    }

    @ParameterizedTest(name = "rechaza RLS: {0}")
    @MethodSource("contextosInvalidos")
    void rechazaIdentidadRolORlsNoVerificados(String escenario, EstadoRls estadoRls) {
        contextoValido(A);
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS), mapperRls())).thenReturn(estadoRls);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isInstanceOf(IllegalStateException.class);

        comprobarRollbackSinNegocio();
    }

    @Test
    void falloDeNegocioProvocaRollbackYSePropagaSinResultadoVacio() {
        contextoValido(A);
        var error = new DataAccessResourceFailureException("Fallo sintetico");
        when(negocio.get()).thenThrow(error);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isSameAs(error);

        verify(gestor).rollback(estado);
        verify(gestor, never()).commit(any());
        verify(pool).softEvictConnections();
    }

    @Test
    void falloDeRollbackTambienExpulsaLasConexiones() {
        baseLimpia();
        doThrow(new IllegalStateException("Error sintetico")).when(jdbc).execute(EjecutorRlsJdbc.ROL_LOCAL);
        var errorRollback = new TransactionSystemException("Rollback sintetico fallido");
        doThrow(errorRollback).when(gestor).rollback(estado);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isSameAs(errorRollback);

        verify(pool).softEvictConnections();
        verifyNoInteractions(negocio);
    }

    @Test
    void falloDeCommitNoDevuelveUnExitoEInvalidaElPool() {
        contextoValido(A);
        when(negocio.get()).thenReturn("ok");
        var error = new TransactionSystemException("Commit sintetico fallido");
        doThrow(error).when(gestor).commit(estado);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isSameAs(error);
        verify(pool).softEvictConnections();
    }

    @Test
    void bloqueaPersistenciaSiNoPuedeExpulsarElPool() {
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_BASE), mapperBase())).thenReturn(null);
        doThrow(new IllegalStateException("Pool sintetico no disponible")).when(pool).softEvictConnections();
        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio)).isInstanceOf(IllegalStateException.class);
        clearInvocations(jdbc, gestor, usuario);

        assertThatThrownBy(() -> ejecutor.ejecutar(A, negocio))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("bloqueada");
        verifyNoInteractions(jdbc, gestor, usuario, negocio);
    }

    @Test
    void unDataSourceDiferenteNoPuedeUsarseConElEjecutor() {
        var otroJdbc = mock(JdbcTemplate.class);
        var otrasConsultas = mock(NamedParameterJdbcTemplate.class);
        when(otrasConsultas.getJdbcTemplate()).thenReturn(otroJdbc);
        when(otroJdbc.getDataSource()).thenReturn(mock(HikariDataSource.class));

        assertThatThrownBy(() -> ejecutor.verificarDataSource(otrasConsultas))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EjecutorRlsJdbc(usuario, gestor, otrasConsultas))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uuidBSeEstableceDeNuevoSinReciclarElContextoDeA() {
        contextoValido(A);
        ejecutor.ejecutar(A, negocio);
        when(usuario.usuarioActual()).thenReturn(B);
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS), mapperRls()))
                .thenReturn(new EstadoRls("authenticated", B, "on", true));

        ejecutor.ejecutar(B, negocio);

        verify(jdbc).queryForObject(EjecutorRlsJdbc.SUB_LOCAL, String.class, A.toString());
        verify(jdbc).queryForObject(EjecutorRlsJdbc.SUB_LOCAL, String.class, B.toString());
        verify(jdbc).queryForObject(EjecutorRlsJdbc.CLAIMS_LOCAL, String.class, claims(B));
    }

    static Stream<String> operaciones() {
        return Stream.of("pedidoCrear", "pedidoBuscar", "pedidoListar", "pedidoActualizar", "pedidoEliminar",
                "usuarioCrear", "usuarioBuscar", "usuarioActualizar");
    }

    @ParameterizedTest
    @MethodSource("operaciones")
    void todosLosMetodosDenieganSinJwtAntesDeConsultar(String operacion) {
        when(usuario.usuarioActual()).thenReturn(null);
        Supplier<Object> ejecutar = operacionRepositorio(operacion);
        clearInvocations(jdbc, consultas);

        assertThatThrownBy(ejecutar::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(jdbc, consultas, gestor);
    }

    @ParameterizedTest
    @MethodSource("operaciones")
    void todosLosMetodosRechazanPropietarioDistintoDelJwt(String operacion) {
        when(usuario.usuarioActual()).thenReturn(B);
        Supplier<Object> ejecutar = operacionRepositorio(operacion);
        clearInvocations(jdbc, consultas);

        assertThatThrownBy(ejecutar::get).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc, consultas, gestor);
    }

    @ParameterizedTest
    @MethodSource("operaciones")
    void todosLosMetodosEjecutanElProtocoloAntesDeLaConsulta(String operacion) {
        contextoValido(A);
        Supplier<Object> ejecutar = operacionRepositorio(operacion);
        when(consultas.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());
        when(consultas.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        when(consultas.update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocacion -> {
                    KeyHolder claves = invocacion.getArgument(2);
                    claves.getKeyList().add(Map.of("id", 1L));
                    return 1;
                });
        clearInvocations(jdbc, consultas);

        ejecutar.get();

        var orden = inOrder(jdbc, consultas, gestor);
        orden.verify(jdbc).queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS), mapperRls());
        if ("pedidoCrear".equals(operacion)) {
            orden.verify(consultas).update(anyString(), any(SqlParameterSource.class),
                    any(KeyHolder.class), any(String[].class));
        } else if ("pedidoActualizar".equals(operacion) || "pedidoEliminar".equals(operacion)) {
            orden.verify(consultas).update(anyString(), any(SqlParameterSource.class));
        } else {
            orden.verify(consultas).query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<Object>>any());
        }
        orden.verify(gestor).commit(estado);
    }

    @Test
    void sqlDeSeguridadEsLocalFijoYVerificaPermisosSinConsultarDatosDeNegocio() {
        assertThat(EjecutorRlsJdbc.ROL_LOCAL).isEqualTo("SET LOCAL ROLE authenticated");
        assertThat(EjecutorRlsJdbc.SUB_LOCAL).contains("'request.jwt.claim.sub', ?, true");
        assertThat(EjecutorRlsJdbc.CLAIMS_LOCAL).contains("'request.jwt.claims', ?, true");
        assertThat(EjecutorRlsJdbc.ESTADO_BASE).contains("rolsuper", "rolbypassrls", "rolinherit", "rolcreaterole",
                "rolcreatedb", "rolreplication", "relowner", "current_role", "session_user");
        assertThat(EjecutorRlsJdbc.ESTADO_RLS).contains("auth.uid()", "current_role", "row_security",
                "row_security_active('public.usuarios'::regclass)", "row_security_active('public.pedidos'::regclass)");
    }

    private void baseLimpia() {
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_BASE), mapperBase()))
                .thenReturn(new EstadoBase("app_jdbc", "app_jdbc", "none", null, "", null, "on", true));
    }

    private void contextoValido(UUID id) {
        baseLimpia();
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.SUB_LOCAL), eq(String.class), anyString()))
                .thenAnswer(invocacion -> invocacion.getArgument(2));
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.CLAIMS_LOCAL), eq(String.class), anyString()))
                .thenAnswer(invocacion -> invocacion.getArgument(2));
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS), mapperRls()))
                .thenReturn(new EstadoRls("authenticated", id, "on", true));
    }

    private void comprobarRollbackSinNegocio() {
        verify(gestor).rollback(estado);
        verify(gestor, never()).commit(any());
        verify(pool).softEvictConnections();
        verifyNoInteractions(negocio);
    }

    private Supplier<Object> operacionRepositorio(String operacion) {
        var pedidos = new RepositorioPedidosJdbc(consultas, ejecutor);
        var usuarios = new RepositorioUsuariosJdbc(consultas, ejecutor);
        LocalDate fecha = LocalDate.of(2026, 1, 1);
        var pedido = new Pedido(1L, A, fecha, fecha, fecha, "Cliente", "Proveedor", BigDecimal.ONE,
                "pendiente", "Pais", "Departamento", "Ciudad", "Calle", BigDecimal.ZERO);
        var nuevo = new Pedido(null, A, fecha, fecha, fecha, "Cliente", "Proveedor", BigDecimal.ONE,
                "pendiente", "Pais", "Departamento", "Ciudad", "Calle", BigDecimal.ZERO);
        var perfil = new Usuario(A, "usuario", "Usuario", "usuario@example.invalid");
        return switch (operacion) {
            case "pedidoCrear" -> () -> pedidos.crear(nuevo);
            case "pedidoBuscar" -> () -> pedidos.buscarPorIdYPropietario(1L, A);
            case "pedidoListar" -> () -> pedidos.listarPorPropietario(A);
            case "pedidoActualizar" -> () -> pedidos.actualizarPorIdYPropietario(1L, A, pedido);
            case "pedidoEliminar" -> () -> pedidos.eliminarPorIdYPropietario(1L, A);
            case "usuarioCrear" -> () -> usuarios.crear(perfil);
            case "usuarioBuscar" -> () -> usuarios.buscarPorId(A);
            case "usuarioActualizar" -> () -> usuarios.actualizar(perfil);
            default -> throw new IllegalArgumentException("Operacion de prueba desconocida.");
        };
    }

    private static String claims(UUID id) {
        return "{\"sub\":\"" + id + "\",\"role\":\"authenticated\"}";
    }

    private static RowMapper<EstadoBase> mapperBase() {
        return ArgumentMatchers.<RowMapper<EstadoBase>>any();
    }

    private static RowMapper<EstadoRls> mapperRls() {
        return ArgumentMatchers.<RowMapper<EstadoRls>>any();
    }

    private interface Negocio extends Supplier<String> {
    }
}
