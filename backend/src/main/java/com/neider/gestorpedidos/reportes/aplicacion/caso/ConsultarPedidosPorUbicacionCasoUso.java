package com.neider.gestorpedidos.reportes.aplicacion.caso;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginaPedidos;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;

public final class ConsultarPedidosPorUbicacionCasoUso implements ConsultarPedidosPorUbicacion {

    private final ConsultasReportes consultas;
    private final IdentidadAutenticada identidad;

    public ConsultarPedidosPorUbicacionCasoUso(ConsultasReportes consultas, IdentidadAutenticada identidad) {
        this.consultas = Objects.requireNonNull(consultas, "El puerto de reportes es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad, "El puerto de identidad es obligatorio.");
    }

    @Override
    public PaginaPedidos ejecutar(FiltroUbicacion filtros, PaginacionReporte paginacion) {
        UUID propietario = identidad.usuarioRequerido();
        if (filtros == null || paginacion == null) {
            throw new ConsultaReporteInvalidaException();
        }
        return PaginaPedidos.desde(consultas.porUbicacion(propietario, filtros, paginacion), propietario, paginacion);
    }
}
