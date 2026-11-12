package com.neider.gestorpedidos.reportes.infraestructura.entrada.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginaPedidos;

public record ReportePedidosRespuesta(List<DetallePedido> contenido, int pagina, int tamano, boolean haySiguiente) {

    static ReportePedidosRespuesta desde(PaginaPedidos resultado) {
        return new ReportePedidosRespuesta(resultado.contenido().stream().map(DetallePedido::desde).toList(),
                resultado.pagina(), resultado.tamano(), resultado.haySiguiente());
    }

    /** Campos reales del pedido, sin UUID del propietario ni datos internos de seguridad. */
    public record DetallePedido(Long id, LocalDate fecha, LocalDate fechaEnvio, LocalDate fechaEntrega,
            String cliente, String proveedor, BigDecimal valor, String estado, String pais, String departamento,
            String ciudad, String nomenclaturaVivienda, BigDecimal propina) {

        static DetallePedido desde(Pedido pedido) {
            return new DetallePedido(pedido.id(), pedido.fecha(), pedido.fechaEnvio(), pedido.fechaEntrega(),
                    pedido.cliente(), pedido.proveedor(), pedido.valor(), pedido.estado(), pedido.pais(),
                    pedido.departamento(), pedido.ciudad(), pedido.nomenclaturaVivienda(), pedido.propina());
        }
    }
}
