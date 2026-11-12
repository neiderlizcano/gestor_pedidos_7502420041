package com.neider.gestorpedidos.configuracion.usuarios;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.usuarios.aplicacion.caso.ActualizarPerfilCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.caso.ConsultarPerfilPropioCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.caso.CrearPerfilCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ActualizarPerfil;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ConsultarPerfilPropio;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.CrearPerfil;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.seguridad.IdentidadUsuariosJwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Cableado del perfil propio con los casos existentes y la identidad JWT. */
@Configuration(proxyBeanMethods = false)
@Profile("persistencia")
public class UsuariosConfiguracion {

    @Bean
    IdentidadAutenticada identidadUsuarios(UsuarioAutenticado usuario) {
        return new IdentidadUsuariosJwt(usuario);
    }

    @Bean
    CrearPerfil crearPerfil(RepositorioUsuarios repositorio, IdentidadAutenticada identidad) {
        return new CrearPerfilCasoUso(repositorio, identidad);
    }

    @Bean
    ConsultarPerfilPropio consultarPerfilPropio(RepositorioUsuarios repositorio, IdentidadAutenticada identidad) {
        return new ConsultarPerfilPropioCasoUso(repositorio, identidad);
    }

    @Bean
    ActualizarPerfil actualizarPerfil(RepositorioUsuarios repositorio, IdentidadAutenticada identidad) {
        return new ActualizarPerfilCasoUso(repositorio, identidad);
    }
}
