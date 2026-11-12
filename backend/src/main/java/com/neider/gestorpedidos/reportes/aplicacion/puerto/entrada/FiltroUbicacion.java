package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

/** Filtros exactos combinados con AND; al menos uno debe estar presente. */
public record FiltroUbicacion(String pais, String departamento, String ciudad) {

    public FiltroUbicacion {
        if ((pais == null && departamento == null && ciudad == null)
                || (pais != null && pais.isBlank()) || (departamento != null && departamento.isBlank())
                || (ciudad != null && ciudad.isBlank())) {
            throw new ConsultaReporteInvalidaException();
        }
    }
}
