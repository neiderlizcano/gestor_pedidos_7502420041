package com.neider.gestorpedidos.usuarios.aplicacion.caso;

import java.util.Objects;

import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilNoEncontradoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ActualizarPerfil;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.DatosPerfilComando;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.neider.gestorpedidos.usuarios.dominio.UsuarioInvalidoException;

/**
 * Caso de uso que actualiza el perfil del usuario autenticado.
 *
 * <p>El identificador y el correo se conservan; solo cambian {@code username}
 * y {@code nombre}.</p>
 */
public final class ActualizarPerfilCasoUso implements ActualizarPerfil {

    private final RepositorioUsuarios repositorio;
    private final IdentidadAutenticada identidad;

    public ActualizarPerfilCasoUso(RepositorioUsuarios repositorio,
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
        Usuario existente = repositorio.buscarPorId(usuario.id())
                .orElseThrow(PerfilNoEncontradoException::new);

        Usuario actualizado = existente.actualizarPerfil(comando.username(),
                comando.nombre());

        return repositorio.actualizar(actualizado)
                .orElseThrow(PerfilNoEncontradoException::new);
    }
}
