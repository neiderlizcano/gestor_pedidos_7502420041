package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

/** Limite del filtro, no una modificacion de las reglas del perfil almacenado. */
public record FiltroNombreUsername(CampoPerfil campo, String termino, CoincidenciaPerfil coincidencia) {

    public static final int MAX_TERMINO = 200;

    public FiltroNombreUsername {
        if (campo == null || coincidencia == null || termino == null || termino.isBlank()
                || termino.length() > MAX_TERMINO) {
            throw new ConsultaReporteInvalidaException();
        }
    }
}
