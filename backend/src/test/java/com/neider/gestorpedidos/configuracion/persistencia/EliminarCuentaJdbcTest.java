package com.neider.gestorpedidos.configuracion.persistencia;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc.EliminarCuentaJdbc;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Ejecutor real con gestor/JDBC simulados: no abre conexiones ni ejecuta SQL real. */
class EliminarCuentaJdbcTest {
    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
    private final HikariDataSource fuente = mock(HikariDataSource.class);
    private final HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    private final JdbcTransactionManager gestor = mock(JdbcTransactionManager.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NamedParameterJdbcTemplate consultas = mock(NamedParameterJdbcTemplate.class);
    private final SimpleTransactionStatus estado = new SimpleTransactionStatus(true);
    private EliminarCuentaJdbc adaptador;

    @BeforeEach
    void preparar() {
        when(gestor.getDataSource()).thenReturn(fuente);
        when(consultas.getJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.getDataSource()).thenReturn(fuente);
        when(usuario.usuarioActual()).thenReturn(A);
        adaptador = new EliminarCuentaJdbc(consultas, new EjecutorRlsJdbc(usuario, gestor, consultas));
        clearInvocations(gestor, jdbc, consultas);
    }

    private void contexto() {
        when(gestor.getTransaction(any())).thenReturn(estado);
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_BASE),
                org.mockito.ArgumentMatchers.<RowMapper<EjecutorRlsJdbc.EstadoBase>>any()))
                .thenReturn(new EjecutorRlsJdbc.EstadoBase("gestor_pedidos_jdbc", "gestor_pedidos_jdbc",
                        "none", null, null, null, "on", true));
        when(jdbc.queryForObject(EjecutorRlsJdbc.SUB_LOCAL, String.class, A.toString())).thenReturn(A.toString());
        String claims = "{\"sub\":\"" + A + "\",\"role\":\"authenticated\"}";
        when(jdbc.queryForObject(EjecutorRlsJdbc.CLAIMS_LOCAL, String.class, claims)).thenReturn(claims);
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS),
                org.mockito.ArgumentMatchers.<RowMapper<EjecutorRlsJdbc.EstadoRls>>any()))
                .thenReturn(new EjecutorRlsJdbc.EstadoRls("authenticated", A, "on", true));
    }

    @Test
    void invocaFuncionSinArgumentosTrasVerificarContextoYCompletaCommit() {
        contexto();
        adaptador.eliminar(A);
        var orden = inOrder(gestor, jdbc, consultas);
        orden.verify(gestor).getTransaction(any());
        orden.verify(jdbc).queryForObject(eq(EjecutorRlsJdbc.ESTADO_BASE), any(RowMapper.class));
        orden.verify(jdbc).execute("SET LOCAL ROLE authenticated");
        orden.verify(jdbc).queryForObject(EjecutorRlsJdbc.SUB_LOCAL, String.class, A.toString());
        orden.verify(jdbc).queryForObject(eq(EjecutorRlsJdbc.CLAIMS_LOCAL), eq(String.class), anyString());
        orden.verify(jdbc).queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS), any(RowMapper.class));
        orden.verify(consultas).queryForObject(eq("SELECT private.delete_my_account()"), eq(Map.of()), any(RowMapper.class));
        orden.verify(gestor).commit(estado);
        verify(gestor, never()).rollback(any());
        verifyNoMoreInteractions(consultas);
    }

    @Test
    void uuidAjenoNoEjecutaSqlNiAbreTransaccion() {
        assertThatThrownBy(() -> adaptador.eliminar(B)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(gestor, jdbc, consultas);
    }

    @Test
    void sinJwtNoEjecutaSql() {
        when(usuario.usuarioActual()).thenReturn(null);
        assertThatThrownBy(() -> adaptador.eliminar(A))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(gestor, jdbc, consultas);
    }

    @Test
    void falloSqlHaceRollbackYSePropaga() {
        contexto();
        when(fuente.getHikariPoolMXBean()).thenReturn(pool);
        var fallo = new DataAccessResourceFailureException("SQL sintetico privado");
        when(consultas.queryForObject(anyString(), anyMap(), any(RowMapper.class))).thenThrow(fallo);
        assertThatThrownBy(() -> adaptador.eliminar(A)).isSameAs(fallo);
        verify(gestor).rollback(estado);
        verify(gestor, never()).commit(any());
        verify(pool).softEvictConnections();
    }

    @Test
    void falloCommitNoDevuelveExito() {
        contexto();
        when(fuente.getHikariPoolMXBean()).thenReturn(pool);
        var fallo = new TransactionSystemException("commit sintetico");
        doThrow(fallo).when(gestor).commit(estado);
        assertThatThrownBy(() -> adaptador.eliminar(A)).isSameAs(fallo);
        verify(pool).softEvictConnections();
    }

    @Test
    void contextoDiscordanteNoInvocaFuncion() {
        contexto();
        when(fuente.getHikariPoolMXBean()).thenReturn(pool);
        when(jdbc.queryForObject(eq(EjecutorRlsJdbc.ESTADO_RLS),
                org.mockito.ArgumentMatchers.<RowMapper<EjecutorRlsJdbc.EstadoRls>>any()))
                .thenReturn(new EjecutorRlsJdbc.EstadoRls("authenticated", B, "on", true));
        assertThatThrownBy(() -> adaptador.eliminar(A)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(consultas);
        verify(gestor).rollback(estado);
    }

    @Test
    void rechazaDataSourceDistinto() {
        NamedParameterJdbcTemplate distinto = mock(NamedParameterJdbcTemplate.class);
        when(distinto.getJdbcTemplate()).thenReturn(mock(JdbcTemplate.class));
        var ejecutor = new EjecutorRlsJdbc(usuario, gestor, consultas);
        assertThatThrownBy(() -> new EliminarCuentaJdbc(distinto, ejecutor))
                .isInstanceOf(IllegalStateException.class);
    }
}
