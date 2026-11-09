package com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada;

import java.util.List;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Puerto de entrada para listar los pedidos del usuario autenticado.
 */
@FunctionalInterface
public interface ListarPedidosPropios {

    /**
     * Devuelve unicamente los pedidos que pertenecen al usuario autenticado.
     */
    List<Pedido> ejecutar();
}
