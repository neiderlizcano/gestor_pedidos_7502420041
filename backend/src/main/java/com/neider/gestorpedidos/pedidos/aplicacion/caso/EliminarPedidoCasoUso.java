package com.neider.gestorpedidos.pedidos.aplicacion.caso;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.error.PedidoNoEncontradoException;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.EliminarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;

/**
 * Caso de uso que elimina un pedido propio.
 */
public final class EliminarPedidoCasoUso implements EliminarPedido {

    private final RepositorioPedidos repositorio;
    private final IdentidadAutenticada identidad;

    public EliminarPedidoCasoUso(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de pedidos es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public void ejecutar(Long id) {
        UUID propietario = identidad.usuarioRequerido();

        if (!repositorio.eliminarPorIdYPropietario(id, propietario)) {
            throw new PedidoNoEncontradoException();
        }
    }
}
