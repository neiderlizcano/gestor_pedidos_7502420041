package com.neider.gestorpedidos.reportes.infraestructura.entrada.rest;

import java.time.LocalDate;
import java.util.Set;

import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FechaBase;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistencia")
@RequestMapping(path = "/api/v1/reportes/pedidos", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReportesPedidosControlador {

    private static final Set<String> PARAMETROS_FECHAS = Set.of("estado", "desde", "hasta", "fechaBase", "pagina", "tamano");
    private static final Set<String> PARAMETROS_UBICACION = Set.of("pais", "departamento", "ciudad", "pagina", "tamano");

    private final ConsultarPedidosPorEstadoFechas estadoFechas;
    private final ConsultarPedidosPorUbicacion ubicacion;

    public ReportesPedidosControlador(ConsultarPedidosPorEstadoFechas estadoFechas, ConsultarPedidosPorUbicacion ubicacion) {
        this.estadoFechas = estadoFechas;
        this.ubicacion = ubicacion;
    }

    @GetMapping("/estado-fechas")
    public ReportePedidosRespuesta porEstadoFechas(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "fechaBase", defaultValue = "fecha") String fechaBase,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamano", defaultValue = "20") int tamano,
            @RequestParam MultiValueMap<String, String> parametros) throws ServletRequestBindingException {
        exigirParametros(parametros, PARAMETROS_FECHAS);
        return ReportePedidosRespuesta.desde(estadoFechas.ejecutar(
                new FiltroEstadoFechas(estado, desde, hasta, FechaBase.desde(fechaBase)), new PaginacionReporte(pagina, tamano)));
    }

    @GetMapping("/ubicacion")
    public ReportePedidosRespuesta porUbicacion(
            @RequestParam(name = "pais", required = false) String pais,
            @RequestParam(name = "departamento", required = false) String departamento,
            @RequestParam(name = "ciudad", required = false) String ciudad,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamano", defaultValue = "20") int tamano,
            @RequestParam MultiValueMap<String, String> parametros) throws ServletRequestBindingException {
        exigirParametros(parametros, PARAMETROS_UBICACION);
        return ReportePedidosRespuesta.desde(ubicacion.ejecutar(
                new FiltroUbicacion(pais, departamento, ciudad), new PaginacionReporte(pagina, tamano)));
    }

    private static void exigirParametros(MultiValueMap<String, String> parametros, Set<String> permitidos)
            throws ServletRequestBindingException {
        if (parametros.entrySet().stream().anyMatch(entrada -> !permitidos.contains(entrada.getKey())
                || entrada.getValue().size() != 1 || entrada.getValue().get(0) == null
                || entrada.getValue().get(0).isBlank())) {
            throw new ServletRequestBindingException("Parametros desconocidos o repetidos en el reporte.");
        }
    }
}
