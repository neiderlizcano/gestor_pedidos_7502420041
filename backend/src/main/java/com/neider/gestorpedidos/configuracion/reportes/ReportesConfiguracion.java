package com.neider.gestorpedidos.configuracion.reportes;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorEstadoFechasCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPedidosPorUbicacionCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPedidosPorUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("persistencia")
public class ReportesConfiguracion {

    @Bean
    IdentidadAutenticada identidadReportes(UsuarioAutenticado usuario) {
        return usuario::usuarioActual;
    }

    @Bean
    ConsultarPedidosPorEstadoFechas consultarPedidosPorEstadoFechas(ConsultasReportes consultas, IdentidadAutenticada identidad) {
        return new ConsultarPedidosPorEstadoFechasCasoUso(consultas, identidad);
    }

    @Bean
    ConsultarPedidosPorUbicacion consultarPedidosPorUbicacion(ConsultasReportes consultas, IdentidadAutenticada identidad) {
        return new ConsultarPedidosPorUbicacionCasoUso(consultas, identidad);
    }
}
