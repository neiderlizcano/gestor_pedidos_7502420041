package com.neider.gestorpedidos.usuarios.aplicacion.caso;

import java.util.List;
import java.util.Objects;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.ListarPerfilPropio;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Caso de uso que lista el perfil del usuario autenticado.
 *
 * <p>Devuelve cero o un elemento porque el identificador autenticado identifica
 * como maximo un perfil.</p>
 */
public final class ListarPerfilPropioCasoUso implements ListarPerfilPropio {

    private final RepositorioUsuarios repositorio;
    private final IdentidadAutenticada identidad;

    public ListarPerfilPropioCasoUso(RepositorioUsuarios repositorio,
            IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de usuarios es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public List<Usuario> ejecutar() {
        IdentidadUsuario usuario = identidad.usuarioRequerido();

        return repositorio.buscarPorId(usuario.id())
                .map(perfil -> List.of(perfil))
                .orElseGet(List::of);
    }
}
