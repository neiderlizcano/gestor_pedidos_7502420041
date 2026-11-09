package com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Puerto de entrada para registrar un pedido del usuario autenticado.
 */
@FunctionalInterface
public interface CrearPedido {

    /**
     * Crea un pedido con los datos indicados.
     *
     * <p>El propietario se obtiene del puerto de identidad y el identificador
     * lo asigna la persistencia.</p>
     */
    Pedido ejecutar(DatosPedidoComando comando);
}
