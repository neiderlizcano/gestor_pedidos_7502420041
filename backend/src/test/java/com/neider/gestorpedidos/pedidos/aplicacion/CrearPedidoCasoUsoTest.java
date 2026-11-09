package com.neider.gestorpedidos.pedidos.aplicacion;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.FECHA;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.FECHA_ENTREGA;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.FECHA_ENVIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.pedidos.aplicacion.caso.CrearPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.DatosPedidoComando;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba;
import com.neider.gestorpedidos.pedidos.aplicacion.soporte.RepositorioPedidosEnMemoria;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.pedidos.dominio.PedidoInvalidoException;

class CrearPedidoCasoUsoTest {

    private RepositorioPedidosEnMemoria repositorio;
    private IdentidadFija identidad;
    private CrearPedidoCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioPedidosEnMemoria();
        identidad = new IdentidadFija(PROPIETARIO);
        casoUso = new CrearPedidoCasoUso(repositorio, identidad);
    }

    @Test
    void creaPedidoPropioConIdentificadorAsignadoYPropietarioDeLaIdentidad() {
        Pedido creado = casoUso.ejecutar(PedidosDePrueba.comando());

        assertThat(creado.id()).isNotNull();
        assertThat(creado.usuarioId()).isEqualTo(PROPIETARIO);
        assertThat(creado.cliente()).isEqualTo("Cliente");
        assertThat(creado.valor()).isEqualByComparingTo("100.50");
        assertThat(identidad.llamadas()).isEqualTo(1);
    }

    @Test
    void persisteElPedidoCreado() {
        Pedido creado = casoUso.ejecutar(PedidosDePrueba.comando());

        assertThat(repositorio.buscarSinFiltrar(creado.id())).contains(creado);
    }

    @Test
    void elPropietarioDependeDeLaIdentidadYNoDelComando() {
        CrearPedidoCasoUso otroCaso = new CrearPedidoCasoUso(repositorio,
                new IdentidadFija(OTRO_USUARIO));

        Pedido creado = otroCaso.ejecutar(PedidosDePrueba.comando());

        assertThat(creado.usuarioId()).isEqualTo(OTRO_USUARIO);
        assertThat(creado.perteneceA(PROPIETARIO)).isFalse();
    }

    @Test
    void reutilizaLasValidacionesDelDominio() {
        DatosPedidoComando invalido = new DatosPedidoComando(
                FECHA, FECHA_ENVIO, FECHA_ENTREGA, "Cliente", "Proveedor",
                BigDecimal.ZERO, "Pendiente", "Colombia", "Santander",
                "Bucaramanga", "Calle 1 #2-3", BigDecimal.ZERO);

        assertThatThrownBy(() -> casoUso.ejecutar(invalido))
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("valor");

        assertThat(repositorio.total()).isZero();
    }

    @Test
    void rechazaComandoNulo() {
        assertThatThrownBy(() -> casoUso.ejecutar(null))
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("datos del pedido");

        assertThat(repositorio.total()).isZero();
    }

    @Test
    void exigeIdentidadAutenticada() {
        CrearPedidoCasoUso sinSesion = new CrearPedidoCasoUso(repositorio,
                new IdentidadFija(null));

        assertThatThrownBy(() -> sinSesion.ejecutar(PedidosDePrueba.comando()))
                .isInstanceOf(UsuarioNoAutenticadoException.class);

        assertThat(repositorio.total()).isZero();
    }
}
