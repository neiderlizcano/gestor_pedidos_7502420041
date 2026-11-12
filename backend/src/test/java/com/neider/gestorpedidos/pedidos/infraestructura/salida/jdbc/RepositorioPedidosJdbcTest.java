package com.neider.gestorpedidos.pedidos.infraestructura.salida.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;

/** Pruebas del adaptador con JDBC simulado; no se abre ninguna conexion. */
@ExtendWith(MockitoExtension.class)
class RepositorioPedidosJdbcTest {

    private static final UUID PROPIETARIO =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTRO_PROPIETARIO =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final Pedido PEDIDO = new Pedido(
            77L,
            PROPIETARIO,
            LocalDate.of(2026, 1, 10),
            LocalDate.of(2026, 1, 11),
            LocalDate.of(2026, 1, 15),
            "Cliente Uno",
            "Proveedor Uno",
            new BigDecimal("1250000.50"),
            "pendiente",
            "Colombia",
            "Cundinamarca",
            "Bogota",
            "Calle 10 # 20-30",
            new BigDecimal("5000.00"));

    private static final Pedido NUEVO = new Pedido(
            null,
            PROPIETARIO,
            PEDIDO.fecha(),
            PEDIDO.fechaEnvio(),
            PEDIDO.fechaEntrega(),
            PEDIDO.cliente(),
            PEDIDO.proveedor(),
            PEDIDO.valor(),
            PEDIDO.estado(),
            PEDIDO.pais(),
            PEDIDO.departamento(),
            PEDIDO.ciudad(),
            PEDIDO.nomenclaturaVivienda(),
            PEDIDO.propina());

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private EjecutorRlsJdbc rls;

    private RepositorioPedidosJdbc repositorio;

    @BeforeEach
    void crearAdaptador() {
        // Estas pruebas conservan las aserciones SQL; el ejecutor se prueba por separado.
        lenient().when(rls.ejecutar(any(UUID.class), any())).thenAnswer(invocacion -> {
            Supplier<?> operacion = invocacion.getArgument(1);
            return operacion.get();
        });
        repositorio = new RepositorioPedidosJdbc(jdbc, rls);
    }

    @Test
    void crearInsertaElPropietarioYDevuelveElIdentificadorGenerado() throws Exception {
        when(jdbc.update(anyString(), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocacion -> {
                    KeyHolder clave = invocacion.getArgument(2);
                    clave.getKeyList().add(Map.<String, Object>of("id", 900L));
                    return 1;
                });

        Pedido creado = repositorio.crear(NUEVO);

        assertThat(creado.id()).isEqualTo(900L);
        assertThat(creado.usuarioId()).isEqualTo(PROPIETARIO);

        ArgumentCaptor<String> sentencia = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        ArgumentCaptor<String[]> columnas = ArgumentCaptor.forClass(String[].class);
        verify(jdbc).update(sentencia.capture(), parametros.capture(),
                any(KeyHolder.class), columnas.capture());

        assertThat(sentencia.getValue())
                .contains("INSERT INTO public.pedidos", ":usuarioId", ":propina")
                .doesNotContain(":id")
                .doesNotContain(PROPIETARIO.toString());
        assertThat(columnas.getValue()).containsExactly("id");
        assertarParametrosDelPedido(parametros.getValue(), NUEVO);
    }

    @Test
    void crearRechazaUnPedidoQueYaTieneIdentificador() {
        Pedido conId = PEDIDO;

        assertThatThrownBy(() -> repositorio.crear(conId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identificador");
        verifyNoInteractions(jdbc);
    }

    @Test
    void crearFallaSiPostgresqlNoDevuelveLaClaveGenerada() throws Exception {
        when(jdbc.update(anyString(), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class)))
                .thenReturn(1);

        assertThatThrownBy(() -> repositorio.crear(NUEVO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identificador");
    }

    @Test
    void buscarFiltraPorIdYPropietarioYMapeaTodasLasColumnas() throws Exception {
        ResultSet fila = filaDe(PEDIDO);
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Pedido>>any()))
                .thenAnswer(invocacion -> {
                    RowMapper<Pedido> mapper = invocacion.getArgument(2);
                    return List.of(mapper.mapRow(fila, 0));
                });

        Optional<Pedido> encontrado =
                repositorio.buscarPorIdYPropietario(PEDIDO.id(), PROPIETARIO);

        assertThat(encontrado).contains(PEDIDO);
        ArgumentCaptor<String> sentencia = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sentencia.capture(), parametros.capture(),
                ArgumentMatchers.<RowMapper<Pedido>>any());

        assertThat(sentencia.getValue())
                .contains("FROM public.pedidos")
                .contains("WHERE id = :id AND usuario_id = :usuarioId");
        assertThat(parametros.getValue().getValue("id")).isEqualTo(77L);
        assertThat(parametros.getValue().getValue("usuarioId")).isEqualTo(PROPIETARIO);
    }

    @Test
    void buscarDevuelveVacioCuandoNoHayCoincidencias() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Pedido>>any()))
                .thenReturn(List.of());

        assertThat(repositorio.buscarPorIdYPropietario(404L, PROPIETARIO)).isEmpty();
    }

    @Test
    void listarFiltraSoloPorElPropietario() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Pedido>>any()))
                .thenReturn(List.of(PEDIDO));

        List<Pedido> pedidos = repositorio.listarPorPropietario(PROPIETARIO);

        assertThat(pedidos).containsExactly(PEDIDO);
        ArgumentCaptor<String> sentencia = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sentencia.capture(), parametros.capture(),
                ArgumentMatchers.<RowMapper<Pedido>>any());

        assertThat(sentencia.getValue())
                .contains("WHERE usuario_id = :usuarioId", "ORDER BY id");
        assertThat(parametros.getValue().getValue("usuarioId")).isEqualTo(PROPIETARIO);
        assertThat(parametros.getValue().hasValue("id")).isFalse();
    }

    @Test
    void actualizarModificaSoloColumnasEditablesYFiltraPorPropietario() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        Optional<Pedido> actualizado =
                repositorio.actualizarPorIdYPropietario(PEDIDO.id(), PROPIETARIO, PEDIDO);

        assertThat(actualizado).contains(PEDIDO);
        ArgumentCaptor<String> sentencia = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sentencia.capture(), parametros.capture());

        String sql = sentencia.getValue();
        int inicioAsignaciones = sql.indexOf("SET");
        int inicioFiltro = sql.indexOf("WHERE");
        assertThat(inicioAsignaciones).isGreaterThanOrEqualTo(0);
        assertThat(inicioFiltro).isGreaterThan(inicioAsignaciones);
        assertThat(sql.substring(inicioAsignaciones, inicioFiltro)).doesNotContain("usuario_id");
        assertThat(sql).contains("WHERE id = :id AND usuario_id = :usuarioId");

        assertThat(parametros.getValue().getValue("id")).isEqualTo(77L);
        assertThat(parametros.getValue().getValue("usuarioId")).isEqualTo(PROPIETARIO);
        assertarParametrosDelPedido(parametros.getValue(), PEDIDO);
    }

    @Test
    void actualizarDevuelveVacioCuandoNoExisteLaFila() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        assertThat(repositorio.actualizarPorIdYPropietario(404L, PROPIETARIO,
                conPropietario(404L, PROPIETARIO))).isEmpty();
    }

    @Test
    void actualizarRechazaUnPedidoDeOtroPropietarioSinConsultarLaBase() {
        Pedido ajeno = conPropietario(PEDIDO.id(), OTRO_PROPIETARIO);

        assertThatThrownBy(() -> repositorio.actualizarPorIdYPropietario(
                PEDIDO.id(), PROPIETARIO, ajeno))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propietario");
        verifyNoInteractions(jdbc);
    }

    @Test
    void eliminarFiltraPorIdYPropietarioYDevuelveSiSeElimino() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        boolean eliminado = repositorio.eliminarPorIdYPropietario(PEDIDO.id(), PROPIETARIO);

        assertThat(eliminado).isTrue();
        ArgumentCaptor<String> sentencia = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sentencia.capture(), parametros.capture());
        assertThat(sentencia.getValue())
                .contains("DELETE FROM public.pedidos")
                .contains("WHERE id = :id AND usuario_id = :usuarioId");
        assertThat(parametros.getValue().getValue("id")).isEqualTo(77L);
        assertThat(parametros.getValue().getValue("usuarioId")).isEqualTo(PROPIETARIO);
    }

    @Test
    void eliminarDevuelveFalseCuandoNoHayCoincidencias() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        assertThat(repositorio.eliminarPorIdYPropietario(404L, PROPIETARIO)).isFalse();
    }

    private static void assertarParametrosDelPedido(SqlParameterSource parametros, Pedido pedido) {
        assertThat(parametros.getValue("usuarioId")).isEqualTo(pedido.usuarioId());
        assertThat(parametros.getValue("fecha")).isEqualTo(pedido.fecha());
        assertThat(parametros.getValue("fechaEnvio")).isEqualTo(pedido.fechaEnvio());
        assertThat(parametros.getValue("fechaEntrega")).isEqualTo(pedido.fechaEntrega());
        assertThat(parametros.getValue("cliente")).isEqualTo(pedido.cliente());
        assertThat(parametros.getValue("proveedor")).isEqualTo(pedido.proveedor());
        assertThat(parametros.getValue("valor")).isEqualTo(pedido.valor());
        assertThat(parametros.getValue("estado")).isEqualTo(pedido.estado());
        assertThat(parametros.getValue("pais")).isEqualTo(pedido.pais());
        assertThat(parametros.getValue("departamento")).isEqualTo(pedido.departamento());
        assertThat(parametros.getValue("ciudad")).isEqualTo(pedido.ciudad());
        assertThat(parametros.getValue("nomenclaturaVivienda"))
                .isEqualTo(pedido.nomenclaturaVivienda());
        assertThat(parametros.getValue("propina")).isEqualTo(pedido.propina());
    }

    private static Pedido conPropietario(Long id, UUID propietario) {
        return new Pedido(id, propietario, PEDIDO.fecha(), PEDIDO.fechaEnvio(),
                PEDIDO.fechaEntrega(), PEDIDO.cliente(), PEDIDO.proveedor(), PEDIDO.valor(),
                PEDIDO.estado(), PEDIDO.pais(), PEDIDO.departamento(), PEDIDO.ciudad(),
                PEDIDO.nomenclaturaVivienda(), PEDIDO.propina());
    }

    private static ResultSet filaDe(Pedido pedido) throws SQLException {
        ResultSet resultado = mock(ResultSet.class);
        when(resultado.getLong("id")).thenReturn(pedido.id());
        when(resultado.getObject("usuario_id", UUID.class)).thenReturn(pedido.usuarioId());
        when(resultado.getObject("fecha", LocalDate.class)).thenReturn(pedido.fecha());
        when(resultado.getObject("fecha_envio", LocalDate.class)).thenReturn(pedido.fechaEnvio());
        when(resultado.getObject("fecha_entrega", LocalDate.class))
                .thenReturn(pedido.fechaEntrega());
        when(resultado.getString("cliente")).thenReturn(pedido.cliente());
        when(resultado.getString("proveedor")).thenReturn(pedido.proveedor());
        when(resultado.getBigDecimal("valor")).thenReturn(pedido.valor());
        when(resultado.getString("estado")).thenReturn(pedido.estado());
        when(resultado.getString("pais")).thenReturn(pedido.pais());
        when(resultado.getString("departamento")).thenReturn(pedido.departamento());
        when(resultado.getString("ciudad")).thenReturn(pedido.ciudad());
        when(resultado.getString("nomenclatura_vivienda"))
                .thenReturn(pedido.nomenclaturaVivienda());
        when(resultado.getBigDecimal("propina")).thenReturn(pedido.propina());
        return resultado;
    }
}
