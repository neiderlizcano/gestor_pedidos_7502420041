package com.neider.gestorpedidos.configuracion.reportes;

import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorCorreoCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.caso.ConsultarPerfilPorNombreUsernameCasoUso;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportesUsuarios;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Reutiliza la identidad de reportes existente sin modificar su cableado ni sus casos de pedidos. */
@Configuration(proxyBeanMethods = false)
@Profile("persistencia")
public class ReportesUsuariosConfiguracion {

    @Bean
    ConsultarPerfilPorNombreUsername consultarPerfilPorNombreUsername(ConsultasReportesUsuarios consultas, IdentidadAutenticada identidad) {
        return new ConsultarPerfilPorNombreUsernameCasoUso(consultas, identidad);
    }

    @Bean
    ConsultarPerfilPorCorreo consultarPerfilPorCorreo(ConsultasReportesUsuarios consultas, IdentidadAutenticada identidad) {
        return new ConsultarPerfilPorCorreoCasoUso(consultas, identidad);
    }
}
