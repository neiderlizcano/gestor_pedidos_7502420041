package com.neider.gestorpedidos.usuarios.aplicacion.caso;

import java.util.Objects;

import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilNoEncontradoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ConsultarPerfilPropio;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Caso de uso que consulta el perfil del usuario autenticado.
 */
public final class ConsultarPerfilPropioCasoUso implements ConsultarPerfilPropio {

    private final RepositorioUsuarios repositorio;
    private final IdentidadAutenticada identidad;

    public ConsultarPerfilPropioCasoUso(RepositorioUsuarios repositorio,
            IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de usuarios es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public Usuario ejecutar() {
        IdentidadUsuario usuario = identidad.usuarioRequerido();

        return repositorio.buscarPorId(usuario.id())
                .orElseThrow(PerfilNoEncontradoException::new);
    }
}
