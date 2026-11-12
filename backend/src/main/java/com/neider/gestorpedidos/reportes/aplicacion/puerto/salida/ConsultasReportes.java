package com.neider.gestorpedidos.reportes.aplicacion.puerto.salida;

import java.util.List;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;

/** Lecturas propias, ordenadas por id, con hasta tamano + 1 filas para detectar la siguiente pagina. */
public interface ConsultasReportes {

    List<Pedido> porEstadoFechas(UUID propietario, FiltroEstadoFechas filtros, PaginacionReporte paginacion);

    List<Pedido> porUbicacion(UUID propietario, FiltroUbicacion filtros, PaginacionReporte paginacion);
}
