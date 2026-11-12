package com.neider.gestorpedidos.configuracion.pedidos;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.ActualizarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.ConsultarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.CrearPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.EliminarPedidoCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.caso.ListarPedidosPropiosCasoUso;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ActualizarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ConsultarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.CrearPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.EliminarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ListarPedidosPropios;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Cableado de pedidos; no activa JDBC ni abre conexiones por si mismo. */
@Configuration(proxyBeanMethods = false)
@Profile("persistencia")
public class PedidosConfiguracion {

    @Bean
    IdentidadAutenticada identidadPedidos(UsuarioAutenticado usuario) {
        return usuario::usuarioActual;
    }

    @Bean
    CrearPedido crearPedido(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        return new CrearPedidoCasoUso(repositorio, identidad);
    }

    @Bean
    ListarPedidosPropios listarPedidosPropios(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        return new ListarPedidosPropiosCasoUso(repositorio, identidad);
    }

    @Bean
    ConsultarPedido consultarPedido(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        return new ConsultarPedidoCasoUso(repositorio, identidad);
    }

    @Bean
    ActualizarPedido actualizarPedido(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        return new ActualizarPedidoCasoUso(repositorio, identidad);
    }

    @Bean
    EliminarPedido eliminarPedido(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        return new EliminarPedidoCasoUso(repositorio, identidad);
    }
}
