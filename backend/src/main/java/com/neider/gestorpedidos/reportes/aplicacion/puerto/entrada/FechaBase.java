package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

/** Fechas reales del pedido; nunca se recibe un identificador SQL arbitrario. */
public enum FechaBase {
    FECHA, FECHA_ENVIO, FECHA_ENTREGA;

    public static FechaBase desde(String valor) {
        if (valor == null) {
            throw new ConsultaReporteInvalidaException();
        }
        return switch (valor) {
            case "fecha" -> FECHA;
            case "fecha_envio" -> FECHA_ENVIO;
            case "fecha_entrega" -> FECHA_ENTREGA;
            default -> throw new ConsultaReporteInvalidaException();
        };
    }
}
