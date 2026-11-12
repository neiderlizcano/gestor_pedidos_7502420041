package com.neider.gestorpedidos.configuracion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** Spring JDBC real sobre Connection/Statement simulados, sin driver ni servidor en ejecucion. */
class EjecutorRlsJdbcConexionTest {

    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final HikariDataSource pool = mock(HikariDataSource.class);
    private final HikariPoolMXBean controlPool = mock(HikariPoolMXBean.class);
    private final Connection conexion = mock(Connection.class);
    private final Statement sentencia = mock(Statement.class);
    private final PreparedStatement sub = mock(PreparedStatement.class);
    private final PreparedStatement claims = mock(PreparedStatement.class);
    private final PreparedStatement negocio = mock(PreparedStatement.class);
    private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
    private final AtomicReference<String> identidadEstablecida = new AtomicReference<>();
    private final AtomicReference<String> jsonEstablecido = new AtomicReference<>();
    private NamedParameterJdbcTemplate consultas;
    private EjecutorRlsJdbc ejecutor;

    @BeforeEach
    void prepararJdbcSimulado() throws SQLException {
        when(pool.getConnection()).thenReturn(conexion);
        when(pool.getHikariPoolMXBean()).thenReturn(controlPool);
        when(conexion.getAutoCommit()).thenReturn(true);
        when(conexion.createStatement()).thenReturn(sentencia);
        when(conexion.prepareStatement(EjecutorRlsJdbc.SUB_LOCAL)).thenReturn(sub);
        when(conexion.prepareStatement(EjecutorRlsJdbc.CLAIMS_LOCAL)).thenReturn(claims);
        when(conexion.prepareStatement("SELECT ?")).thenReturn(negocio);
        doAnswer(invocacion -> {
            identidadEstablecida.set(invocacion.getArgument(1));
            return null;
        }).when(sub).setString(org.mockito.ArgumentMatchers.eq(1), anyString());
        doAnswer(invocacion -> {
            jsonEstablecido.set(invocacion.getArgument(1));
            return null;
        }).when(claims).setString(org.mockito.ArgumentMatchers.eq(1), anyString());
        when(sub.executeQuery()).thenAnswer(invocacion -> texto(identidadEstablecida.get()));
        when(claims.executeQuery()).thenAnswer(invocacion -> texto(jsonEstablecido.get()));
        when(negocio.executeQuery()).thenAnswer(invocacion -> texto("ok"));
        when(sentencia.executeQuery(EjecutorRlsJdbc.ESTADO_BASE)).thenAnswer(invocacion -> baseLimpia());
        when(sentencia.executeQuery(EjecutorRlsJdbc.ESTADO_RLS)).thenAnswer(invocacion -> rlsLocal());
        when(usuario.usuarioActual()).thenReturn(A);
        consultas = new NamedParameterJdbcTemplate(pool);
        ejecutor = new EjecutorRlsJdbc(usuario, new JdbcTransactionManager(pool), consultas);
    }

    @Test
    void springLigaRolClaimsYConsultaALaMismaConexionHastaElCommit() throws SQLException {
        assertThat(ejecutor.ejecutar(A, this::consultar)).isEqualTo("ok");

        var orden = inOrder(conexion, sentencia, sub, claims, negocio);
        orden.verify(conexion).setAutoCommit(false);
        orden.verify(sentencia).executeQuery(EjecutorRlsJdbc.ESTADO_BASE);
        orden.verify(sentencia).execute(EjecutorRlsJdbc.ROL_LOCAL);
        orden.verify(sub).setString(1, A.toString());
        orden.verify(claims).setString(1, "{\"sub\":\"" + A + "\",\"role\":\"authenticated\"}");
        orden.verify(sentencia).executeQuery(EjecutorRlsJdbc.ESTADO_RLS);
        orden.verify(negocio).executeQuery();
        orden.verify(conexion).commit();
        orden.verify(conexion).setAutoCommit(true);
        orden.verify(conexion).close();
        verify(pool, times(1)).getConnection();
    }

    @Test
    void falloDeNegocioHaceRollbackYLiberaLaConexionMedianteSpring() throws SQLException {
        var error = new IllegalStateException("Error sintetico");

        assertThatThrownBy(() -> ejecutor.ejecutar(A, () -> { throw error; })).isSameAs(error);

        var orden = inOrder(conexion, controlPool);
        orden.verify(conexion).rollback();
        orden.verify(conexion).setAutoCommit(true);
        orden.verify(conexion).close();
        orden.verify(controlPool).softEvictConnections();
        verify(pool, times(1)).getConnection();
    }

    @Test
    void reutilizarLaConexionVuelveAEnlazarElUuidDelSegundoUsuario() throws SQLException {
        ejecutor.ejecutar(A, this::consultar);
        when(usuario.usuarioActual()).thenReturn(B);

        assertThat(ejecutor.ejecutar(B, this::consultar)).isEqualTo("ok");

        verify(sub).setString(1, A.toString());
        verify(sub).setString(1, B.toString());
        verify(pool, times(2)).getConnection();
        verify(conexion, times(2)).commit();
        verify(conexion, times(2)).close();
        // Solo acredita el enlace de Spring y los parametros; no simula las politicas PostgreSQL.
    }

    private String consultar() {
        return consultas.queryForObject("SELECT :valor", new MapSqlParameterSource("valor", "ok"), String.class);
    }

    private ResultSet baseLimpia() throws SQLException {
        ResultSet rs = fila();
        when(rs.getString("rol_actual")).thenReturn("app_jdbc");
        when(rs.getString("rol_sesion")).thenReturn("app_jdbc");
        when(rs.getString("rol_configurado")).thenReturn("none");
        when(rs.getString("rls")).thenReturn("on");
        when(rs.getBoolean("login_seguro")).thenReturn(true);
        return rs;
    }

    private ResultSet rlsLocal() throws SQLException {
        ResultSet rs = fila();
        when(rs.getString("rol_actual")).thenReturn("authenticated");
        when(rs.getObject("usuario", UUID.class)).thenReturn(UUID.fromString(identidadEstablecida.get()));
        when(rs.getString("rls")).thenReturn("on");
        when(rs.getBoolean("tablas_protegidas")).thenReturn(true);
        return rs;
    }

    private ResultSet texto(String valor) throws SQLException {
        ResultSet rs = fila();
        var metadatos = mock(ResultSetMetaData.class);
        when(metadatos.getColumnCount()).thenReturn(1);
        when(rs.getMetaData()).thenReturn(metadatos);
        when(rs.getString(1)).thenReturn(valor);
        return rs;
    }

    private ResultSet fila() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        return rs;
    }
}
