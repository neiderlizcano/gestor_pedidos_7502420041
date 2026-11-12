package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

public enum CoincidenciaPerfil {
    EXACTA, PREFIJO;

    public static CoincidenciaPerfil desde(String valor) {
        if (valor == null) throw new ConsultaReporteInvalidaException();
        return switch (valor) {
            case "exacta" -> EXACTA;
            case "prefijo" -> PREFIJO;
            default -> throw new ConsultaReporteInvalidaException();
        };
    }
}
