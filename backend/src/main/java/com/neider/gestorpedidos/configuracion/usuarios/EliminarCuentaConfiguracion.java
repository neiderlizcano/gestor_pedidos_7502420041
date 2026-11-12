package com.neider.gestorpedidos.configuracion.usuarios;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.usuarios.aplicacion.caso.EliminarCuentaPropiaCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.EliminarCuentaPropia;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadCuentaAutenticada;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("persistencia")
public class EliminarCuentaConfiguracion {
    @Bean
    IdentidadCuentaAutenticada identidadCuenta(UsuarioAutenticado usuario) {
        return usuario::usuarioActual;
    }

    @Bean
    EliminarCuentaPropia eliminarCuentaPropia(EliminarCuenta cuentas, IdentidadCuentaAutenticada identidad) {
        return new EliminarCuentaPropiaCasoUso(cuentas, identidad);
    }
}
