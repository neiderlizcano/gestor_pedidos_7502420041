package com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.pedido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FechaBase;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Solo JDBC simulado: verifica SQL y el paso obligatorio por RLS, no simula politicas PostgreSQL. */
class ConsultasReportesJdbcTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 1, 31);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final EjecutorRlsJdbc rls = mock(EjecutorRlsJdbc.class);
    private final ConsultasReportesJdbc consultas = new ConsultasReportesJdbc(jdbc, rls);

    @AfterEach
    void limpiarIdentidad() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"FECHA,fecha", "FECHA_ENVIO,fecha_envio", "FECHA_ENTREGA,fecha_entrega"})
    void fechasSeSeleccionanPorListaCerradaYTodosLosValoresSeParametrizan(FechaBase base, String columna) {
        habilitarEjecutor();
        String estado = "Pendiente' OR 1=1 --";

        consultas.porEstadoFechas(PROPIETARIO, new FiltroEstadoFechas(estado, DESDE, HASTA, base), new PaginacionReporte(2, 10));

        var sql = ArgumentCaptor.forClass(String.class);
        var parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parametros.capture(), ArgumentMatchers.<RowMapper<Pedido>>any());
        assertThat(sql.getValue()).contains("FROM public.pedidos WHERE usuario_id = :usuarioId", "estado = :estado",
                columna + " >= :desde", columna + " <= :hasta", "ORDER BY id ASC LIMIT :limite OFFSET :desplazamiento")
                .doesNotContain(estado, PROPIETARIO.toString(), "2026-01", " OR ");
        assertThat(parametros.getValue().getValue("estado")).isEqualTo(estado);
        assertThat(parametros.getValue().getValue("desde")).isEqualTo(DESDE);
        assertThat(parametros.getValue().getValue("hasta")).isEqualTo(HASTA);
        comprobarPropietarioYPaginacion(parametros.getValue(), 11, 20L);
        verify(rls).ejecutar(org.mockito.ArgumentMatchers.eq(PROPIETARIO), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void ubicacionesIndividualesYCombinadasSonParametrosConjuntos(int mascara) {
        habilitarEjecutor();
        String valor = "x' OR '1'='1";
        var filtros = new FiltroUbicacion((mascara & 1) != 0 ? valor : null,
                (mascara & 2) != 0 ? valor : null, (mascara & 4) != 0 ? valor : null);

        consultas.porUbicacion(PROPIETARIO, filtros, new PaginacionReporte(0, 20));

        var sql = ArgumentCaptor.forClass(String.class);
        var parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parametros.capture(), ArgumentMatchers.<RowMapper<Pedido>>any());
        assertThat(sql.getValue()).contains("WHERE usuario_id = :usuarioId", "ORDER BY id ASC LIMIT :limite OFFSET :desplazamiento")
                .doesNotContain(valor, PROPIETARIO.toString(), " OR ");
        String[] campos = {"pais", "departamento", "ciudad"};
        for (int i = 0; i < campos.length; i++) {
            String campo = campos[i];
            if ((mascara & (1 << i)) != 0) {
                assertThat(sql.getValue()).contains(" AND " + campo + " = :" + campo);
                assertThat(parametros.getValue().getValue(campo)).isEqualTo(valor);
            } else {
                assertThat(sql.getValue()).doesNotContain(":" + campo);
            }
        }
        comprobarPropietarioYPaginacion(parametros.getValue(), 21, 0L);
        verify(rls).ejecutar(org.mockito.ArgumentMatchers.eq(PROPIETARIO), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void estadoYLimitesOpcionalesNoIntroducenPredicadosParaValoresAusentes(int mascara) {
        habilitarEjecutor();
        consultas.porEstadoFechas(PROPIETARIO, new FiltroEstadoFechas((mascara & 1) != 0 ? "Pendiente" : null,
                (mascara & 2) != 0 ? DESDE : null, (mascara & 4) != 0 ? HASTA : null, FechaBase.FECHA),
                new PaginacionReporte(0, 100));

        var sql = ArgumentCaptor.forClass(String.class);
        var parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parametros.capture(), ArgumentMatchers.<RowMapper<Pedido>>any());
        assertThat(sql.getValue().contains(":estado")).isEqualTo((mascara & 1) != 0);
        assertThat(sql.getValue().contains(":desde")).isEqualTo((mascara & 2) != 0);
        assertThat(sql.getValue().contains(":hasta")).isEqualTo((mascara & 4) != 0);
        comprobarPropietarioYPaginacion(parametros.getValue(), 101, 0L);
    }

    @Test
    void desplazamientoGrandeSeLigaSinDesbordarEnteros() {
        habilitarEjecutor();
        consultas.porUbicacion(PROPIETARIO, new FiltroUbicacion(null, null, "Ciudad"),
                new PaginacionReporte(Integer.MAX_VALUE, 100));
        var parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), parametros.capture(), ArgumentMatchers.<RowMapper<Pedido>>any());
        comprobarPropietarioYPaginacion(parametros.getValue(), 101, 214748364700L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void consultaYMapeoOcurrenSoloDentroDelEjecutor(String reporte) throws SQLException {
        AtomicBoolean dentro = new AtomicBoolean();
        when(rls.ejecutar(any(UUID.class), any())).thenAnswer(invocacion -> {
            dentro.set(true);
            try {
                return ((Supplier<?>) invocacion.getArgument(1)).get();
            } finally {
                dentro.set(false);
            }
        });
        Pedido esperado = pedido(7L, PROPIETARIO);
        ResultSet fila = filaDe(esperado);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Pedido>>any()))
                .thenAnswer(invocacion -> {
                    assertThat(dentro).isTrue();
                    RowMapper<Pedido> mapper = invocacion.getArgument(2);
                    return List.of(mapper.mapRow(fila, 0));
                });

        assertThat(ejecutar(consultas, reporte)).containsExactly(esperado);
        assertThat(dentro).isFalse();
        verify(rls).verificarDataSource(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void siElEjecutorNoAutorizaNoSeEjecutaJdbc(String reporte) {
        when(rls.ejecutar(any(UUID.class), any())).thenThrow(new AccessDeniedException("Rechazo sintetico"));
        assertThatThrownBy(() -> ejecutar(consultas, reporte)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void ejecutorRealRechazaSinJwtYConPropietarioDiscordanteSinAbrirConexiones(String reporte) {
        var pool = mock(HikariDataSource.class);
        var plantilla = new NamedParameterJdbcTemplate(pool);
        var real = new EjecutorRlsJdbc(new UsuarioAutenticado(), new JdbcTransactionManager(pool), plantilla);
        var adaptador = new ConsultasReportesJdbc(plantilla, real);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> ejecutar(adaptador, reporte)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        Jwt jwt = Jwt.withTokenValue("identidad-sintetica-no-es-jwt").header("alg", "ES256").subject(OTRO_USUARIO.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        assertThatThrownBy(() -> ejecutar(adaptador, reporte)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(pool);
    }

    @Test
    void noPermiteUnDataSourceDistintoAlDelEjecutor() {
        var otraPlantilla = mock(NamedParameterJdbcTemplate.class);
        doThrow(new IllegalStateException("DataSource distinto")).when(rls).verificarDataSource(otraPlantilla);
        assertThatThrownBy(() -> new ConsultasReportesJdbc(otraPlantilla, rls)).isInstanceOf(IllegalStateException.class);
    }

    private void habilitarEjecutor() {
        when(rls.ejecutar(any(UUID.class), any())).thenAnswer(invocacion -> ((Supplier<?>) invocacion.getArgument(1)).get());
    }

    private static List<Pedido> ejecutar(ConsultasReportesJdbc adaptador, String reporte) {
        var pagina = new PaginacionReporte(0, 20);
        return "estado".equals(reporte)
                ? adaptador.porEstadoFechas(PROPIETARIO, new FiltroEstadoFechas(null, DESDE, HASTA, FechaBase.FECHA), pagina)
                : adaptador.porUbicacion(PROPIETARIO, new FiltroUbicacion("Colombia", null, null), pagina);
    }

    private static void comprobarPropietarioYPaginacion(SqlParameterSource parametros, int limite, long desplazamiento) {
        assertThat(parametros.getValue("usuarioId")).isEqualTo(PROPIETARIO);
        assertThat(parametros.getValue("limite")).isEqualTo(limite);
        assertThat(parametros.getValue("desplazamiento")).isEqualTo(desplazamiento);
    }

    private static ResultSet filaDe(Pedido pedido) throws SQLException {
        ResultSet fila = mock(ResultSet.class);
        when(fila.getLong("id")).thenReturn(pedido.id());
        when(fila.getObject("usuario_id", UUID.class)).thenReturn(pedido.usuarioId());
        when(fila.getObject("fecha", LocalDate.class)).thenReturn(pedido.fecha());
        when(fila.getObject("fecha_envio", LocalDate.class)).thenReturn(pedido.fechaEnvio());
        when(fila.getObject("fecha_entrega", LocalDate.class)).thenReturn(pedido.fechaEntrega());
        when(fila.getString("cliente")).thenReturn(pedido.cliente());
        when(fila.getString("proveedor")).thenReturn(pedido.proveedor());
        when(fila.getBigDecimal("valor")).thenReturn(pedido.valor());
        when(fila.getString("estado")).thenReturn(pedido.estado());
        when(fila.getString("pais")).thenReturn(pedido.pais());
        when(fila.getString("departamento")).thenReturn(pedido.departamento());
        when(fila.getString("ciudad")).thenReturn(pedido.ciudad());
        when(fila.getString("nomenclatura_vivienda")).thenReturn(pedido.nomenclaturaVivienda());
        when(fila.getBigDecimal("propina")).thenReturn(pedido.propina());
        return fila;
    }
}
