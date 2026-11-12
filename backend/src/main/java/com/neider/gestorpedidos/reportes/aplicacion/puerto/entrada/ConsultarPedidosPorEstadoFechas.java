package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

@FunctionalInterface
public interface ConsultarPedidosPorEstadoFechas {

    PaginaPedidos ejecutar(FiltroEstadoFechas filtros, PaginacionReporte paginacion);
}
