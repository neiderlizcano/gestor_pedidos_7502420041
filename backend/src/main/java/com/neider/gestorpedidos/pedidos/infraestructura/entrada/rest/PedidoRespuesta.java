package com.neider.gestorpedidos.pedidos.infraestructura.entrada.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/** Representacion publica de un pedido propio, sin identidad interna del propietario. */
public record PedidoRespuesta(
        Long id,
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

    static PedidoRespuesta desde(Pedido pedido) {
        return new PedidoRespuesta(pedido.id(), pedido.fecha(), pedido.fechaEnvio(), pedido.fechaEntrega(),
                pedido.cliente(), pedido.proveedor(), pedido.valor(), pedido.estado(), pedido.pais(),
                pedido.departamento(), pedido.ciudad(), pedido.nomenclaturaVivienda(), pedido.propina());
    }
}
