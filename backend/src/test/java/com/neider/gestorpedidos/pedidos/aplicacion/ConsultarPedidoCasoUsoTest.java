package com.neider.gestorpedidos.pedidos.aplicacion;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.pedidos.aplicacion.caso.ConsultarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.CrearPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.error.PedidoNoEncontradoException;
import com.neider.gestorpedidos.pedidos.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.RepositorioPedidosEnMemoria;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

class ConsultarPedidoCasoUsoTest {

    private RepositorioPedidosEnMemoria repositorio;
    private ConsultarPedidoCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioPedidosEnMemoria();
        casoUso = new ConsultarPedidoCasoUso(repositorio, new IdentidadFija(PROPIETARIO));
    }

    @Test
    void consultaUnPedidoPropio() {
        Pedido pedido = PedidosDePrueba.pedido(10L, PROPIETARIO);
        repositorio.sembrar(pedido);

        Pedido encontrado = casoUso.ejecutar(10L);

        assertThat(encontrado).isEqualTo(pedido);
    }

    @Test
    void creaYConsultaUnPedidoPropio() {
        CrearPedidoCasoUso crear = new CrearPedidoCasoUso(repositorio,
                new IdentidadFija(PROPIETARIO));

        Pedido creado = crear.ejecutar(PedidosDePrueba.comando());
        Pedido consultado = casoUso.ejecutar(creado.id());

        assertThat(consultado).isEqualTo(creado);
        assertThat(consultado.usuarioId()).isEqualTo(PROPIETARIO);
    }

    @Test
    void ocultaPedidosDeOtroUsuario() {
        repositorio.sembrar(PedidosDePrueba.pedido(20L, OTRO_USUARIO));

        assertThatThrownBy(() -> casoUso.ejecutar(20L))
                .isInstanceOf(PedidoNoEncontradoException.class);
    }

    @Test
    void rechazaPedidoInexistente() {
        assertThatThrownBy(() -> casoUso.ejecutar(99L))
                .isInstanceOf(PedidoNoEncontradoException.class);
    }

    @Test
    void exigeIdentidadAutenticada() {
        ConsultarPedidoCasoUso sinSesion = new ConsultarPedidoCasoUso(repositorio,
                new IdentidadFija(null));

        assertThatThrownBy(() -> sinSesion.ejecutar(10L))
                .isInstanceOf(UsuarioNoAutenticadoException.class);
    }
}
