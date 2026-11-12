package com.neider.gestorpedidos.reportes.aplicacion.caso;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginaPedidos;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;

public final class ConsultarPedidosPorEstadoFechasCasoUso implements ConsultarPedidosPorEstadoFechas {

    private final ConsultasReportes consultas;
    private final IdentidadAutenticada identidad;

    public ConsultarPedidosPorEstadoFechasCasoUso(ConsultasReportes consultas, IdentidadAutenticada identidad) {
        this.consultas = Objects.requireNonNull(consultas, "El puerto de reportes es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad, "El puerto de identidad es obligatorio.");
    }

    @Override
    public PaginaPedidos ejecutar(FiltroEstadoFechas filtros, PaginacionReporte paginacion) {
        UUID propietario = identidad.usuarioRequerido();
        if (filtros == null || paginacion == null) {
            throw new ConsultaReporteInvalidaException();
        }
        return PaginaPedidos.desde(consultas.porEstadoFechas(propietario, filtros, paginacion), propietario, paginacion);
    }
}
