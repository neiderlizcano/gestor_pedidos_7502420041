package com.neider.gestorpedidos.pedidos.aplicacion;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.pedidos.aplicacion.caso.EliminarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.error.PedidoNoEncontradoException;
import com.neider.gestorpedidos.pedidos.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.RepositorioPedidosEnMemoria;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

class EliminarPedidoCasoUsoTest {

    private RepositorioPedidosEnMemoria repositorio;
    private EliminarPedidoCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioPedidosEnMemoria();
        casoUso = new EliminarPedidoCasoUso(repositorio, new IdentidadFija(PROPIETARIO));
    }

    @Test
    void eliminaPedidoPropio() {
        repositorio.sembrar(PedidosDePrueba.pedido(10L, PROPIETARIO));

        casoUso.ejecutar(10L);

        assertThat(repositorio.total()).isZero();
        assertThat(repositorio.buscarSinFiltrar(10L)).isEmpty();
    }

    @Test
    void noEliminaPedidoDeOtroUsuario() {
        Pedido ajeno = PedidosDePrueba.pedido(20L, OTRO_USUARIO);
        repositorio.sembrar(ajeno);

        assertThatThrownBy(() -> casoUso.ejecutar(20L))
                .isInstanceOf(PedidoNoEncontradoException.class);

        assertThat(repositorio.buscarSinFiltrar(20L)).contains(ajeno);
    }

    @Test
    void rechazaPedidoInexistente() {
        assertThatThrownBy(() -> casoUso.ejecutar(99L))
                .isInstanceOf(PedidoNoEncontradoException.class);
    }

    @Test
    void rechazaEliminarDosVecesElMismoPedido() {
        repositorio.sembrar(PedidosDePrueba.pedido(10L, PROPIETARIO));
        casoUso.ejecutar(10L);

        assertThatThrownBy(() -> casoUso.ejecutar(10L))
                .isInstanceOf(PedidoNoEncontradoException.class);
    }

    @Test
    void exigeIdentidadAutenticada() {
        EliminarPedidoCasoUso sinSesion = new EliminarPedidoCasoUso(repositorio,
                new IdentidadFija(null));

        assertThatThrownBy(() -> sinSesion.ejecutar(10L))
                .isInstanceOf(UsuarioNoAutenticadoException.class);
    }
}
