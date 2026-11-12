package com.neider.gestorpedidos.configuracion.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorEstadoFechasCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorUbicacionCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc.ConsultasReportesJdbc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ReportesConfiguracionTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final EjecutorRlsJdbc rls = mock(EjecutorRlsJdbc.class);
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ReportesConfiguracion.class, ConsultasReportesJdbc.class)
            .withBean(UsuarioAutenticado.class)
            .withBean(NamedParameterJdbcTemplate.class, () -> jdbc)
            .withBean(EjecutorRlsJdbc.class, () -> rls);

    @Test
    void sinPerfilNoActivaReportes() {
        contexto.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(ConsultarPedidosPorEstadoFechas.class).doesNotHaveBean(ConsultarPedidosPorUbicacion.class)
                .doesNotHaveBean(ConsultasReportes.class).doesNotHaveBean(IdentidadAutenticada.class));
        verifyNoInteractions(jdbc, rls);
    }

    @Test
    void perfilConectaCasosYAdaptadorConElEjecutorSinAbrirConexiones() {
        contexto.withPropertyValues("spring.profiles.active=persistencia").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class)
                    .hasSingleBean(IdentidadAutenticada.class).hasSingleBean(ConsultasReportes.class);
            assertThat(context.getBean(ConsultarPedidosPorEstadoFechas.class)).isInstanceOf(ConsultarPedidosPorEstadoFechasCasoUso.class);
            assertThat(context.getBean(ConsultarPedidosPorUbicacion.class)).isInstanceOf(ConsultarPedidosPorUbicacionCasoUso.class);
            assertThat(context.getBean(ConsultasReportes.class)).isInstanceOf(ConsultasReportesJdbc.class);
            verify(rls).verificarDataSource(jdbc);
            verifyNoInteractions(jdbc);
        });
    }
}
