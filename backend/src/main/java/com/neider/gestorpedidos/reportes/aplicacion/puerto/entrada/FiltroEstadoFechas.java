package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import java.time.LocalDate;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

/** Limites inclusivos, opcionales e independientes; estado es texto libre del modelo. */
public record FiltroEstadoFechas(String estado, LocalDate desde, LocalDate hasta, FechaBase fechaBase) {

    public FiltroEstadoFechas {
        if (fechaBase == null || (estado != null && estado.isBlank())
                || (desde != null && hasta != null && desde.isAfter(hasta))) {
            throw new ConsultaReporteInvalidaException();
        }
    }
}
