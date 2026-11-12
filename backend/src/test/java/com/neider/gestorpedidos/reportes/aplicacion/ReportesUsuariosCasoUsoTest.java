package com.neider.gestorpedidos.reportes.aplicacion;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.perfil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorCorreoCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorNombreUsernameCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CampoPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CoincidenciaPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportesUsuarios;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportesUsuariosCasoUsoTest {

    private final ConsultasReportesUsuarios consultas = mock(ConsultasReportesUsuarios.class);
    private final IdentidadAutenticada identidad = mock(IdentidadAutenticada.class, CALLS_REAL_METHODS);
    private final ConsultarPerfilPorNombreUsernameCasoUso nombres = new ConsultarPerfilPorNombreUsernameCasoUso(consultas, identidad);
    private final ConsultarPerfilPorCorreoCasoUso correos = new ConsultarPerfilPorCorreoCasoUso(consultas, identidad);
    private final FiltroNombreUsername filtroNombre = new FiltroNombreUsername(CampoPerfil.NOMBRE, "Neider", CoincidenciaPerfil.PREFIJO);
    private final FiltroCorreo filtroCorreo = new FiltroCorreo(EMAIL);

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void obtienePropietarioDelPuertoDeIdentidadYDevuelveUnaColeccionInmutable(String reporte) {
        when(identidad.usuarioActual()).thenReturn(ID);
        responder(List.of(perfil()));

        List<Usuario> resultado = ejecutar(reporte);

        assertThat(resultado).containsExactly(perfil());
        assertThatThrownBy(resultado::clear).isInstanceOf(UnsupportedOperationException.class);
        if ("nombre".equals(reporte)) {
            verify(consultas).porNombreUsername(ID, filtroNombre);
        } else {
            verify(consultas).porCorreo(ID, filtroCorreo);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void sinIdentidadNoSeConsultaPersistencia(String reporte) {
        assertThatThrownBy(() -> ejecutar(reporte)).isInstanceOf(UsuarioNoAutenticadoException.class);
        verifyNoInteractions(consultas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void sinCoincidenciasDevuelveColeccionVacia(String reporte) {
        when(identidad.usuarioActual()).thenReturn(ID);
        responder(List.of());
        assertThat(ejecutar(reporte)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void fallaCerradoSiUnAdaptadorDevuelveUnPerfilAjeno(String reporte) {
        when(identidad.usuarioActual()).thenReturn(ID);
        responder(List.of(perfil(OTRO_ID, "otro", "Otro", OTRO_EMAIL)));
        assertThatThrownBy(() -> ejecutar(reporte)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "correo"})
    void noPermiteMasDeUnPerfilAunqueElAdaptadorSeaDefectuoso(String reporte) {
        when(identidad.usuarioActual()).thenReturn(ID);
        responder(List.of(perfil(), perfil()));
        assertThatThrownBy(() -> ejecutar(reporte)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filtrosNulosNoConsultanLaSalida() {
        when(identidad.usuarioActual()).thenReturn(ID);
        assertThatThrownBy(() -> nombres.ejecutar(null)).isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> correos.ejecutar(null)).isInstanceOf(ConsultaReporteInvalidaException.class);
        verifyNoInteractions(consultas);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void textoObligatorioNoAdmiteNulosNiBlancos(String texto) {
        assertThatThrownBy(() -> new FiltroNombreUsername(CampoPerfil.NOMBRE, texto, CoincidenciaPerfil.EXACTA))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroCorreo(texto)).isInstanceOf(ConsultaReporteInvalidaException.class);
    }

    @Test
    void limitaLongitudSinTruncarNiCambiarReglasDelDominio() {
        assertThat(new FiltroNombreUsername(CampoPerfil.USERNAME, "x".repeat(200), CoincidenciaPerfil.PREFIJO).termino())
                .hasSize(200);
        assertThat(new FiltroCorreo("x".repeat(254)).correo()).hasSize(254);
        assertThatThrownBy(() -> new FiltroNombreUsername(CampoPerfil.NOMBRE, "x".repeat(201), CoincidenciaPerfil.EXACTA))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroCorreo("x".repeat(255))).isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroNombreUsername(null, "Valido", CoincidenciaPerfil.EXACTA))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> new FiltroNombreUsername(CampoPerfil.NOMBRE, "Valido", null))
                .isInstanceOf(ConsultaReporteInvalidaException.class);
    }

    @Test
    void listasCerradasDeCamposYCoincidencias() {
        assertThat(CampoPerfil.desde("nombre")).isEqualTo(CampoPerfil.NOMBRE);
        assertThat(CampoPerfil.desde("username")).isEqualTo(CampoPerfil.USERNAME);
        assertThat(CoincidenciaPerfil.desde("exacta")).isEqualTo(CoincidenciaPerfil.EXACTA);
        assertThat(CoincidenciaPerfil.desde("prefijo")).isEqualTo(CoincidenciaPerfil.PREFIJO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "email", "id", "NOMBRE", "nombre; DROP TABLE usuarios", "ILIKE", "contiene"})
    void noAdmiteIdentificadoresUOperadoresSqlArbitrarios(String texto) {
        assertThatThrownBy(() -> CampoPerfil.desde(texto)).isInstanceOf(ConsultaReporteInvalidaException.class);
        assertThatThrownBy(() -> CoincidenciaPerfil.desde(texto)).isInstanceOf(ConsultaReporteInvalidaException.class);
    }

    private List<Usuario> ejecutar(String reporte) {
        return "nombre".equals(reporte) ? nombres.ejecutar(filtroNombre) : correos.ejecutar(filtroCorreo);
    }

    private void responder(List<Usuario> perfiles) {
        when(consultas.porNombreUsername(any(), any())).thenReturn(perfiles);
        when(consultas.porCorreo(any(), any())).thenReturn(perfiles);
    }
}
