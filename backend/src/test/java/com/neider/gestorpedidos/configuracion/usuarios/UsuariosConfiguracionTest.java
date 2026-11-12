package com.neider.gestorpedidos.configuracion.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;

import com.neider.gestorpedidos.configuracion.pedidos.PedidosConfiguracion;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.usuarios.aplicacion.caso.ActualizarPerfilCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.caso.ConsultarPerfilPropioCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.caso.CrearPerfilCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ActualizarPerfil;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ConsultarPerfilPropio;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.CrearPerfil;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.seguridad.IdentidadUsuariosJwt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UsuariosConfiguracionTest {

    private final RepositorioUsuarios usuarios = mock(RepositorioUsuarios.class);
    private final com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos pedidos =
            mock(com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos.class);
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(UsuariosConfiguracion.class, PedidosConfiguracion.class)
            .withBean(UsuarioAutenticado.class)
            .withBean(RepositorioUsuarios.class, () -> usuarios)
            .withBean(com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos.class, () -> pedidos);

    @Test
    void sinPerfilNoActivaLosPuertosDeUsuarios() {
        contexto.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CrearPerfil.class)
                .doesNotHaveBean(ConsultarPerfilPropio.class).doesNotHaveBean(ActualizarPerfil.class)
                .doesNotHaveBean(IdentidadAutenticada.class));
    }

    @Test
    void perfilConectaUsuariosYPedidosSinAmbiguedadNiConexiones() {
        contexto.withPropertyValues("spring.profiles.active=persistencia").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class)
                    .hasSingleBean(IdentidadAutenticada.class)
                    .hasSingleBean(com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada.class);
            assertThat(context.getBean(IdentidadAutenticada.class)).isInstanceOf(IdentidadUsuariosJwt.class);
            assertThat(context.getBean(CrearPerfil.class)).isInstanceOf(CrearPerfilCasoUso.class);
            assertThat(context.getBean(ConsultarPerfilPropio.class)).isInstanceOf(ConsultarPerfilPropioCasoUso.class);
            assertThat(context.getBean(ActualizarPerfil.class)).isInstanceOf(ActualizarPerfilCasoUso.class);
            verifyNoInteractions(usuarios, pedidos);
        });
    }
}
