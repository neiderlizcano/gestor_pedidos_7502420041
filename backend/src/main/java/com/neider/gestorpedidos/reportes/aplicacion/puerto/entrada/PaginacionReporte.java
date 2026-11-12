package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;

public record PaginacionReporte(int pagina, int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    public PaginacionReporte {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ConsultaReporteInvalidaException();
        }
    }

    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
