package com.neider.gestorpedidos.pedidos.aplicacion.soporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.DatosPedidoComando;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Datos de ejemplo compartidos por las pruebas de la capa de aplicacion.
 */
public final class PedidosDePrueba {

    public static final UUID PROPIETARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTRO_USUARIO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final LocalDate FECHA = LocalDate.of(2026, 1, 10);
    public static final LocalDate FECHA_ENVIO = LocalDate.of(2026, 1, 11);
    public static final LocalDate FECHA_ENTREGA = LocalDate.of(2026, 1, 12);

    private PedidosDePrueba() {
    }

    public static Pedido pedido(Long id, UUID propietarioId) {
        return new Pedido(id, propietarioId, FECHA, FECHA_ENVIO, FECHA_ENTREGA,
                "Cliente", "Proveedor", new BigDecimal("100.50"), "Pendiente",
                "Colombia", "Santander", "Bucaramanga", "Calle 1 #2-3",
                new BigDecimal("2.00"));
    }

    public static DatosPedidoComando comando() {
        return new DatosPedidoComando(FECHA, FECHA_ENVIO, FECHA_ENTREGA,
                "Cliente", "Proveedor", new BigDecimal("100.50"), "Pendiente",
                "Colombia", "Santander", "Bucaramanga", "Calle 1 #2-3",
                new BigDecimal("2.00"));
    }

    public static DatosPedidoComando comando(String cliente, BigDecimal valor) {
        return new DatosPedidoComando(FECHA, FECHA_ENVIO, FECHA_ENTREGA,
                cliente, "Proveedor", valor, "Pendiente",
                "Colombia", "Santander", "Bucaramanga", "Calle 1 #2-3",
                new BigDecimal("2.00"));
    }
}
