package com.neider.gestorpedidos.usuarios.infraestructura.salida.seguridad;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Lee exclusivamente la identidad del JWT ya validado por Spring Security. */
public final class IdentidadUsuariosJwt implements IdentidadAutenticada {

    private final UsuarioAutenticado usuario;

    public IdentidadUsuariosJwt(UsuarioAutenticado usuario) {
        this.usuario = Objects.requireNonNull(usuario, "La fuente de identidad es obligatoria.");
    }

    @Override
    public IdentidadUsuario usuarioActual() {
        UUID id = usuario.usuarioActual();
        if (id == null || !(SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            return null;
        }
        // Solo el claim superior de Auth firmado: nunca user_metadata ni datos HTTP del cliente.
        Object correo = jwt.getToken().getClaims().get("email");
        if (!(correo instanceof String email) || email.isBlank()) {
            throw new UsuarioNoAutenticadoException();
        }
        return new IdentidadUsuario(id, email);
    }
}
