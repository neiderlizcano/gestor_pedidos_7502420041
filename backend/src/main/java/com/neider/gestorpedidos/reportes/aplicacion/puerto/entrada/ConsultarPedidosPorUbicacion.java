package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

@FunctionalInterface
public interface ConsultarPedidosPorUbicacion {

    PaginaPedidos ejecutar(FiltroUbicacion filtros, PaginacionReporte paginacion);
}
