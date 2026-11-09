package com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Puerto de entrada para actualizar un pedido propio.
 */
@FunctionalInterface
public interface ActualizarPedido {

    /**
     * Actualiza los datos editables del pedido indicado.
     *
     * <p>El identificador y el propietario se conservan; el propietario se
     * obtiene del puerto de identidad.</p>
     */
    Pedido ejecutar(Long id, DatosPedidoComando comando);
}
