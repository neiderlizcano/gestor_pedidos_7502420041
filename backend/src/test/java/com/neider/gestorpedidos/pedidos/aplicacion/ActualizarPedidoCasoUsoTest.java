package com.neider.gestorpedidos.pedidos.aplicacion;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.pedidos.aplicacion.caso.ActualizarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.error.PedidoNoEncontradoException;
import com.neider.gestorpedidos.pedidos.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.RepositorioPedidosEnMemoria;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.pedidos.dominio.PedidoInvalidoException;

class ActualizarPedidoCasoUsoTest {

    private RepositorioPedidosEnMemoria repositorio;
    private ActualizarPedidoCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioPedidosEnMemoria();
        casoUso = new ActualizarPedidoCasoUso(repositorio, new IdentidadFija(PROPIETARIO));
    }

    @Test
    void actualizaPedidoPropioConservandoIdentificadorYPropietario() {
        repositorio.sembrar(PedidosDePrueba.pedido(10L, PROPIETARIO));

        Pedido actualizado = casoUso.ejecutar(10L,
                PedidosDePrueba.comando("Nuevo cliente", new BigDecimal("250.00")));

        assertThat(actualizado.id()).isEqualTo(10L);
        assertThat(actualizado.usuarioId()).isEqualTo(PROPIETARIO);
        assertThat(actualizado.cliente()).isEqualTo("Nuevo cliente");
        assertThat(actualizado.valor()).isEqualByComparingTo("250.00");
        assertThat(repositorio.buscarSinFiltrar(10L)).contains(actualizado);
    }

    @Test
    void noPermiteActualizarPedidoDeOtroUsuario() {
        Pedido ajeno = PedidosDePrueba.pedido(20L, OTRO_USUARIO);
        repositorio.sembrar(ajeno);

        assertThatThrownBy(() -> casoUso.ejecutar(20L,
                PedidosDePrueba.comando("Intruso", new BigDecimal("1.00"))))
                .isInstanceOf(PedidoNoEncontradoException.class);

        assertThat(repositorio.buscarSinFiltrar(20L)).contains(ajeno);
    }

    @Test
    void unSegundoUsuarioNoPuedeCambiarElPropietario() {
        repositorio.sembrar(PedidosDePrueba.pedido(10L, PROPIETARIO));
        ActualizarPedidoCasoUso otroCaso = new ActualizarPedidoCasoUso(repositorio,
                new IdentidadFija(OTRO_USUARIO));

        assertThatThrownBy(() -> otroCaso.ejecutar(10L, PedidosDePrueba.comando()))
                .isInstanceOf(PedidoNoEncontradoException.class);

        Pedido guardado = repositorio.buscarSinFiltrar(10L).orElseThrow();
        assertThat(guardado.usuarioId()).isEqualTo(PROPIETARIO);
    }

    @Test
    void rechazaPedidoInexistente() {
        assertThatThrownBy(() -> casoUso.ejecutar(99L, PedidosDePrueba.comando()))
                .isInstanceOf(PedidoNoEncontradoException.class);
    }

    @Test
    void reutilizaLasValidacionesDelDominio() {
        repositorio.sembrar(PedidosDePrueba.pedido(10L, PROPIETARIO));

        assertThatThrownBy(() -> casoUso.ejecutar(10L,
                PedidosDePrueba.comando("Cliente", BigDecimal.ZERO)))
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("valor");

        Pedido guardado = repositorio.buscarSinFiltrar(10L).orElseThrow();
        assertThat(guardado.cliente()).isEqualTo("Cliente");
        assertThat(guardado.valor()).isEqualByComparingTo("100.50");
    }

    @Test
    void rechazaComandoNulo() {
        assertThatThrownBy(() -> casoUso.ejecutar(10L, null))
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("datos del pedido");
    }

    @Test
    void exigeIdentidadAutenticada() {
        ActualizarPedidoCasoUso sinSesion = new ActualizarPedidoCasoUso(repositorio,
                new IdentidadFija(null));

        assertThatThrownBy(() -> sinSesion.ejecutar(10L, PedidosDePrueba.comando()))
                .isInstanceOf(UsuarioNoAutenticadoException.class);
    }
}
