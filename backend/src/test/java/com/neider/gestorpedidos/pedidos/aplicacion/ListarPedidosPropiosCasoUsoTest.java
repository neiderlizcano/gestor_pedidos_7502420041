package com.neider.gestorpedidos.pedidos.aplicacion;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.pedidos.aplicacion.caso.ListarPedidosPropiosCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.RepositorioPedidosEnMemoria;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

class ListarPedidosPropiosCasoUsoTest {

    private RepositorioPedidosEnMemoria repositorio;
    private ListarPedidosPropiosCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioPedidosEnMemoria();
        casoUso = new ListarPedidosPropiosCasoUso(repositorio,
                new IdentidadFija(PROPIETARIO));
    }

    @Test
    void listaSoloLosPedidosDelUsuarioAutenticado() {
        repositorio.sembrar(PedidosDePrueba.pedido(1L, PROPIETARIO));
        repositorio.sembrar(PedidosDePrueba.pedido(2L, PROPIETARIO));
        repositorio.sembrar(PedidosDePrueba.pedido(3L, OTRO_USUARIO));

        List<Pedido> pedidos = casoUso.ejecutar();

        assertThat(pedidos).hasSize(2);
        assertThat(pedidos).allMatch(pedido -> pedido.perteneceA(PROPIETARIO));
        assertThat(pedidos).extracting(Pedido::id).containsExactly(1L, 2L);
    }

    @Test
    void devuelveListaVaciaCuandoNoHayPedidosPropios() {
        repositorio.sembrar(PedidosDePrueba.pedido(3L, OTRO_USUARIO));

        List<Pedido> pedidos = casoUso.ejecutar();

        assertThat(pedidos).isEmpty();
    }

    @Test
    void exigeIdentidadAutenticada() {
        ListarPedidosPropiosCasoUso sinSesion = new ListarPedidosPropiosCasoUso(
                repositorio, new IdentidadFija(null));

        assertThatThrownBy(sinSesion::ejecutar)
                .isInstanceOf(UsuarioNoAutenticadoException.class);
    }
}
