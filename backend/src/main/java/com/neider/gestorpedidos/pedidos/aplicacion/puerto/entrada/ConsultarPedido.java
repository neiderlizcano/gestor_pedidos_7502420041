package com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Puerto de entrada para consultar un pedido propio por su identificador.
 */
@FunctionalInterface
public interface ConsultarPedido {

    /**
     * Devuelve el pedido solicitado si pertenece al usuario autenticado.
     */
    Pedido ejecutar(Long id);
}
