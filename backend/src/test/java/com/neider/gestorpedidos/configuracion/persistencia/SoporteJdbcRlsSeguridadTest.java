package com.neider.gestorpedidos.configuracion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.opentest4j.TestAbortedException;

/** Comprueba protecciones del soporte sin leer configuracion JDBC ni abrir conexiones. */
class SoporteJdbcRlsSeguridadTest {

    @Test
    @DisabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION", matches = "true")
    void sinBanderaLaPreparacionSeDetieneAntesDeCrearInfraestructura() {
        var soporte = new SoporteJdbcRlsIT() { };
        assertThatThrownBy(soporte::prepararSoloConAutorizacionExplicita)
                .isInstanceOf(TestAbortedException.class).hasMessage("Integracion JDBC desactivada.");
        assertThat(soporte.pool).isNull();
        assertThat(soporte.jdbc).isNull();
        assertThat(soporte.ejecutor).isNull();
    }

    @Test
    void erroresDelDriverODatosNoSePublicanComoCausas() {
        assertThatThrownBy(() -> SoporteJdbcRlsIT.protegido(() -> {
            throw new IllegalStateException("detalle-sintetico-no-publicable");
        })).isInstanceOf(AssertionError.class)
                .hasMessage("Fallo de integracion JDBC/RLS; revisar configuracion externa y permisos.").hasNoCause();
    }

    @Test
    void lasOmisionesJustificadasSeConservan() {
        var omitida = new TestAbortedException("Datos sinteticos no disponibles.");
        assertThatThrownBy(() -> SoporteJdbcRlsIT.protegido(() -> { throw omitida; })).isSameAs(omitida);
    }

    @Test
    void lasComprobacionesPropiasConservanSoloMensajesSeguros() {
        assertThatThrownBy(() -> SoporteJdbcRlsIT.protegido(() ->
                SoporteJdbcRlsIT.exigir(false, "Comprobacion sintetica segura.")))
                .isInstanceOf(AssertionError.class).hasMessage("Comprobacion sintetica segura.").hasNoCause();
    }
}
