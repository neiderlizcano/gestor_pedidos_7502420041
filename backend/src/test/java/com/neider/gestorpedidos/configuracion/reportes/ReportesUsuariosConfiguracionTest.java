package com.neider.gestorpedidos.configuracion.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorCorreoCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorNombreUsernameCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportesUsuarios;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc.ConsultasReportesJdbc;
import com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc.ConsultasReportesUsuariosJdbc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ReportesUsuariosConfiguracionTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final EjecutorRlsJdbc rls = mock(EjecutorRlsJdbc.class);
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ReportesConfiguracion.class, ReportesUsuariosConfiguracion.class,
                    ConsultasReportesJdbc.class, ConsultasReportesUsuariosJdbc.class)
            .withBean(UsuarioAutenticado.class)
            .withBean(NamedParameterJdbcTemplate.class, () -> jdbc)
            .withBean(EjecutorRlsJdbc.class, () -> rls);

    @Test
    void sinPerfilNoActivaLosReportesDeUsuarios() {
        contexto.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ConsultarPerfilPorCorreo.class)
                .doesNotHaveBean(ConsultarPerfilPorNombreUsername.class).doesNotHaveBean(ConsultasReportesUsuarios.class));
        verifyNoInteractions(jdbc, rls);
    }

    @Test
    void perfilIntegraLosCuatroReportesConLaMismaIdentidadSinConexiones() {
        contexto.withPropertyValues("spring.profiles.active=persistencia").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class)
                    .hasSingleBean(IdentidadAutenticada.class).hasSingleBean(ConsultasReportesUsuarios.class)
                    .hasSingleBean(ConsultasReportes.class).hasSingleBean(ConsultarPedidosPorEstadoFechas.class)
                    .hasSingleBean(ConsultarPedidosPorUbicacion.class);
            assertThat(context.getBean(ConsultarPerfilPorCorreo.class)).isInstanceOf(ConsultarPerfilPorCorreoCasoUso.class);
            assertThat(context.getBean(ConsultarPerfilPorNombreUsername.class)).isInstanceOf(ConsultarPerfilPorNombreUsernameCasoUso.class);
            verify(rls, times(2)).verificarDataSource(jdbc);
            verifyNoInteractions(jdbc);
        });
    }
}
