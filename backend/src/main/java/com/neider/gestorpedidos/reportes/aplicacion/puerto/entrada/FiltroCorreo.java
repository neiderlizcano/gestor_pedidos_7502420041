package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

public record FiltroCorreo(String correo) {

    public static final int MAX_CORREO = 254;

    public FiltroCorreo {
        if (correo == null || correo.isBlank() || correo.length() > MAX_CORREO) {
            throw new ConsultaReporteInvalidaException();
        }
    }
}
