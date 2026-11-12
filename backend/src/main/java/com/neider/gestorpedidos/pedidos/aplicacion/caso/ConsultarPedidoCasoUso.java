package com.neider.gestorpedidos.pedidos.aplicacion.caso;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.error.PedidoNoEncontradoException;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ConsultarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Caso de uso que consulta un pedido propio por su identificador.
 */
public final class ConsultarPedidoCasoUso implements ConsultarPedido {

    private final RepositorioPedidos repositorio;
    private final IdentidadAutenticada identidad;

    public ConsultarPedidoCasoUso(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de pedidos es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public Pedido ejecutar(Long id) {
        UUID propietario = identidad.usuarioRequerido();
        return repositorio.buscarPorIdYPropietario(id, propietario)
                .orElseThrow(PedidoNoEncontradoException::new);
    }
}
