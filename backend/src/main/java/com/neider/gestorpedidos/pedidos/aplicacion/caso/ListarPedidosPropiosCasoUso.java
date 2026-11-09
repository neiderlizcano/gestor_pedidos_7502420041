package com.neider.gestorpedidos.pedidos.aplicacion.caso;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ListarPedidosPropios;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Caso de uso que lista los pedidos del usuario autenticado.
 */
public final class ListarPedidosPropiosCasoUso implements ListarPedidosPropios {

    private final RepositorioPedidos repositorio;
    private final IdentidadAutenticada identidad;

    public ListarPedidosPropiosCasoUso(RepositorioPedidos repositorio,
            IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de pedidos es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public List<Pedido> ejecutar() {
        UUID propietario = identidad.usuarioRequerido();
        return List.copyOf(repositorio.listarPorPropietario(propietario));
    }
}
