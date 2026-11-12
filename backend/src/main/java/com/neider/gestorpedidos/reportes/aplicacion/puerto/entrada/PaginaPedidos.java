package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import java.util.List;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/** Pagina sin conteo global: la fila adicional solo indica si hay una pagina siguiente. */
public record PaginaPedidos(List<Pedido> contenido, int pagina, int tamano, boolean haySiguiente) {

    public PaginaPedidos {
        contenido = List.copyOf(contenido);
    }

    public static PaginaPedidos desde(List<Pedido> filas, UUID propietario, PaginacionReporte paginacion) {
        // Fallar cerrado ante un adaptador defectuoso; no ocultar un fallo de aislamiento filtrando en memoria.
        if (filas.stream().anyMatch(pedido -> !pedido.perteneceA(propietario))) {
            throw new IllegalStateException("El reporte no conserva el aislamiento del propietario.");
        }
        boolean siguiente = filas.size() > paginacion.tamano();
        return new PaginaPedidos(filas.subList(0, Math.min(filas.size(), paginacion.tamano())),
                paginacion.pagina(), paginacion.tamano(), siguiente);
    }
}
