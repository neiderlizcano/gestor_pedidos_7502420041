package com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos editables de un pedido recibidos desde el exterior.
 *
 * <p>No contiene el identificador ni el propietario: el identificador lo
 * asigna la persistencia y el propietario se obtiene del puerto de identidad,
 * nunca de los datos enviados por el cliente.</p>
 */
public record DatosPedidoComando(
        LocalDate fecha,
        LocalDate fechaEnvio,
        LocalDate fechaEntrega,
        String cliente,
        String proveedor,
        BigDecimal valor,
        String estado,
        String pais,
        String departamento,
        String ciudad,
        String nomenclaturaVivienda,
        BigDecimal propina) {
}
