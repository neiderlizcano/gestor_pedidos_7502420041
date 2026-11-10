package com.neider.gestorpedidos.usuarios.aplicacion.caso;

import java.util.Objects;

import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilConflictoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.CrearPerfil;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.DatosPerfilComando;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.neider.gestorpedidos.usuarios.dominio.UsuarioInvalidoException;

/**
 * Caso de uso que crea el perfil del usuario autenticado.
 */
public final class CrearPerfilCasoUso implements CrearPerfil {

    private final RepositorioUsuarios repositorio;
    private final IdentidadAutenticada identidad;

    public CrearPerfilCasoUso(RepositorioUsuarios repositorio,
            IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de usuarios es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public Usuario ejecutar(DatosPerfilComando comando) {
        if (comando == null) {
            throw new UsuarioInvalidoException("Los datos del perfil son obligatorios.");
        }

        IdentidadUsuario usuario = identidad.usuarioRequerido();
        Usuario perfil = new Usuario(usuario.id(), comando.username(),
                comando.nombre(), usuario.email());

        return repositorio.crear(perfil).orElseThrow(PerfilConflictoException::new);
    }
}
