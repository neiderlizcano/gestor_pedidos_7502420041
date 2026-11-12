package com.neider.gestorpedidos.reportes.aplicacion;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.pedido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorEstadoFechasCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorUbicacionCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FechaBase;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginaPedidos;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportesPedidosCasoUsoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 1, 10);
    private final ConsultasReportes consultas = mock(ConsultasReportes.class);
    private final IdentidadAutenticada identidad = mock(IdentidadAutenticada.class, CALLS_REAL_METHODS);
    private final FiltroEstadoFechas fechas = new FiltroEstadoFechas("Pendiente", DIA, DIA, FechaBase.FECHA);
    private final FiltroUbicacion ubicacion = new FiltroUbicacion("Colombia", null, null);
    private final ConsultarPedidosPorEstadoFechasCasoUso porFechas = new ConsultarPedidosPorEstadoFechasCasoUso(consultas, identidad);
    private final ConsultarPedidosPorUbicacionCasoUso porUbicacion = new ConsultarPedidosPorUbicacionCasoUso(consultas, identidad);

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void obtienePropietarioSoloDeLaIdentidadYRecortaLaFilaExtra(String reporte) {
        when(identidad.usuarioActual()).thenReturn(PROPIETARIO);
        responder(List.of(pedido(1L, PROPIETARIO), pedido(2L, PROPIETARIO), pedido(3L, PROPIETARIO)));
        var paginacion = new PaginacionReporte(3, 2);

        PaginaPedidos resultado = ejecutar(reporte, paginacion);

        assertThat(resultado.contenido()).extracting(Pedido::id).containsExactly(1L, 2L);
        assertThat(resultado.pagina()).isEqualTo(3);
        assertThat(resultado.tamano()).isEqualTo(2);
        assertThat(resultado.haySiguiente()).isTrue();
        assertThatThrownBy(() -> resultado.contenido().clear()).isInstanceOf(UnsupportedOperationException.class);
        if ("estado".equals(reporte)) {
            verify(consultas).porEstadoFechas(PROPIETARIO, fechas, paginacion);
        } else {
            verify(consultas).porUbicacion(PROPIETARIO, ubicacion, paginacion);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void sinIdentidadNoConsultaElPuertoDeSalida(String reporte) {
        assertThatThrownBy(() -> ejecutar(reporte, new PaginacionReporte(0, 20)))
                .isInstanceOf(UsuarioNoAutenticadoException.class);
        verifyNoInteractions(consultas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void resultadosVaciosConservanLaPaginaSolicitada(String reporte) {
        when(identidad.usuarioActual()).thenReturn(PROPIETARIO);
        responder(List.of());

        PaginaPedidos resultado = ejecutar(reporte, new PaginacionReporte(7, 20));

        assertThat(resultado.contenido()).isEmpty();
        assertThat(resultado.pagina()).isEqualTo(7);
        assertThat(resultado.haySiguiente()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void paginaExactaSinFilaExtraNoAnunciaOtraPagina(String reporte) {
        when(identidad.usuarioActual()).thenReturn(PROPIETARIO);
        responder(List.of(pedido(1L, PROPIETARIO)));
        assertThat(ejecutar(reporte, new PaginacionReporte(0, 1)).haySiguiente()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "ubicacion"})
    void unAdaptadorDefectuosoNoPuedeExponerPedidosAjenosNiComoFilaExtra(String reporte) {
        when(identidad.usuarioActual()).thenReturn(PROPIETARIO);
        responder(List.of(pedido(1L, PROPIETARIO), pedido(2L, OTRO_USUARIO)));
        assertThatThrownBy(() -> ejecutar(reporte, new PaginacionReporte(0, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaFiltrosOPaginacionNulosSinAccederALaSalida() {
        when(identidad.usuarioActual()).thenReturn(PROPIETARIO);
        assertThatThrownBy(() -> porFechas.ejecutar(null, new PaginacionReporte(0, 20)))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> porFechas.ejecutar(fechas, null)).isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> porUbicacion.ejecutar(null, new PaginacionReporte(0, 20)))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> porUbicacion.ejecutar(ubicacion, null)).isInstanceOf(ConsultaReporteInvalidaException.class);
        verifyNoInteractions(consultas);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "0,2147483647"})
    void validaLimitesDePaginacion(int pagina, int tamano) {
        assertThatThrownBy(() -> new PaginacionReporte(pagina, tamano))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
    }

    @Test
    void desplazamientoNoDesbordaEnteros() {
        assertThat(new PaginacionReporte(Integer.MAX_VALUE, 100).desplazamiento()).isEqualTo(214748364700L);
    }

    @ParameterizedTest
    @CsvSource({"fecha,FECHA", "fecha_envio,FECHA_ENVIO", "fecha_entrega,FECHA_ENTREGA"})
    void fechasBaseCorrespondenSoloALasTresFechasReales(String valor, FechaBase esperado) {
        assertThat(FechaBase.desde(valor)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "FECHA", "fechaEnvio", "fecha; DROP TABLE pedidos", "usuario_id"})
    void fechaBaseNoAdmiteIdentificadoresArbitrarios(String valor) {
        assertThatThrownBy(() -> FechaBase.desde(valor)).isInstanceOf(ConsultaReporteInvalidaException.class);
    }

    @Test
    void validaRangosYPermiteLimitesIgualesOAbiertos() {
        assertThatThrownBy(() -> new FiltroEstadoFechas(null, DIA.plusDays(1), DIA, FechaBase.FECHA))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroEstadoFechas(null, null, null, null))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThat(new FiltroEstadoFechas(null, DIA, DIA, FechaBase.FECHA).desde()).isEqualTo(DIA);
        assertThat(new FiltroEstadoFechas(null, DIA, null, FechaBase.FECHA).hasta()).isNull();
        assertThat(new FiltroEstadoFechas(null, null, DIA, FechaBase.FECHA).desde()).isNull();
        assertThat(new FiltroEstadoFechas("Estado libre", null, null, FechaBase.FECHA).estado()).isEqualTo("Estado libre");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void filtrosPresentesEnBlancoSonInvalidos(String texto) {
        assertThatThrownBy(() -> new FiltroEstadoFechas(texto, null, null, FechaBase.FECHA))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroUbicacion(texto, null, null)).isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroUbicacion(null, texto, null)).isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroUbicacion(null, null, texto)).isInstanceOf(ConsultaReporteInvalidaException.class);
    }

    @Test
    void ubicacionExigeAlMenosUnFiltro() {
        assertThatThrownBy(() -> new FiltroUbicacion(null, null, null)).isInstanceOf(ConsultaReporteInvalidaException.class);
    }

    private PaginaPedidos ejecutar(String reporte, PaginacionReporte paginacion) {
        return "estado".equals(reporte) ? porFechas.ejecutar(fechas, paginacion) : porUbicacion.ejecutar(ubicacion, paginacion);
    }

    private void responder(List<Pedido> filas) {
        when(consultas.porEstadoFechas(any(), any(), any())).thenReturn(filas);
        when(consultas.porUbicacion(any(), any(), any())).thenReturn(filas);
    }
}
