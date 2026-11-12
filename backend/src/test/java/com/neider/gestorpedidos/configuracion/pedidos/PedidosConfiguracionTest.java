package com.neider.gestorpedidos.configuracion.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.ActualizarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.ConsultarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.CrearPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.EliminarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.ListarPedidosPropiosCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ActualizarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ConsultarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.CrearPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.EliminarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ListarPedidosPropios;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PedidosConfiguracionTest {

    private final RepositorioPedidos repositorio = mock(RepositorioPedidos.class);
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(PedidosConfiguracion.class)
            .withBean(UsuarioAutenticado.class)
            .withBean(RepositorioPedidos.class, () -> repositorio);

    @Test
    void sinPerfilNoActivaLosPuertosDeEntrada() {
        contexto.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(CrearPedido.class).doesNotHaveBean(ConsultarPedido.class)
                .doesNotHaveBean(ListarPedidosPropios.class).doesNotHaveBean(ActualizarPedido.class)
                .doesNotHaveBean(EliminarPedido.class).doesNotHaveBean(IdentidadAutenticada.class));
    }

    @Test
    void perfilConectaLosCasosExistentesSinAccederALaBaseDeDatos() {
        contexto.withPropertyValues("spring.profiles.active=persistencia").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class)
                    .hasSingleBean(IdentidadAutenticada.class);
            assertThat(context.getBean(CrearPedido.class)).isInstanceOf(CrearPedidoCasoUso.class);
            assertThat(context.getBean(ConsultarPedido.class)).isInstanceOf(ConsultarPedidoCasoUso.class);
            assertThat(context.getBean(ListarPedidosPropios.class)).isInstanceOf(ListarPedidosPropiosCasoUso.class);
            assertThat(context.getBean(ActualizarPedido.class)).isInstanceOf(ActualizarPedidoCasoUso.class);
            assertThat(context.getBean(EliminarPedido.class)).isInstanceOf(EliminarPedidoCasoUso.class);
            verifyNoInteractions(repositorio);
        });
    }
}
