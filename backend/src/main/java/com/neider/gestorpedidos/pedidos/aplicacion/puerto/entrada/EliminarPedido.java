package com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada;

/**
 * Puerto de entrada para eliminar un pedido propio.
 */
@FunctionalInterface
public interface EliminarPedido {

    /**
     * Elimina el pedido indicado si pertenece al usuario autenticado.
     */
    void ejecutar(Long id);
}
