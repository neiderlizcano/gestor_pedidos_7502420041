package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

public enum CampoPerfil {
    NOMBRE, USERNAME;

    public static CampoPerfil desde(String valor) {
        if (valor == null) throw new ConsultaReporteInvalidaException();
        return switch (valor) {
            case "nombre" -> NOMBRE;
            case "username" -> USERNAME;
            default -> throw new ConsultaReporteInvalidaException();
        };
    }
}
