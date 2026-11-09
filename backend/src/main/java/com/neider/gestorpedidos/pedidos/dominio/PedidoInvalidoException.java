package com.neider.gestorpedidos.pedidos.dominio;

/**
 * Indica que los datos de un pedido no cumplen las reglas del dominio.
 */
public class PedidoInvalidoException extends RuntimeException {

    public PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
