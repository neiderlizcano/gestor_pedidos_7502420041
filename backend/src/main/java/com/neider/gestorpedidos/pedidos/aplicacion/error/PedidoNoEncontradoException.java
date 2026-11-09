package com.neider.gestorpedidos.pedidos.aplicacion.error;

/**
 * Indica que el pedido solicitado no existe o no pertenece al usuario
 * autenticado.
 *
 * <p>Se usa el mismo error en ambos casos para no revelar la existencia de
 * pedidos ajenos.</p>
 */
public class PedidoNoEncontradoException extends RuntimeException {

    public PedidoNoEncontradoException() {
        super("No existe el pedido solicitado para el usuario autenticado.");
    }
}
