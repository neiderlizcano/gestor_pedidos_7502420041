package com.neider.gestorpedidos.reportes.aplicacion.error;

public class ConsultaReporteInvalidaException extends RuntimeException {

    public ConsultaReporteInvalidaException() {
        super("Los parametros del reporte no son validos.");
    }
}
