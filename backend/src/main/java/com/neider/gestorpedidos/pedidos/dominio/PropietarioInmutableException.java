package com.neider.gestorpedidos.pedidos.dominio;

/**
 * Indica un intento de cambiar el propietario de un pedido existente.
 */
public class PropietarioInmutableException extends PedidoInvalidoException {

    public PropietarioInmutableException(String mensaje) {
        super(mensaje);
    }
}
