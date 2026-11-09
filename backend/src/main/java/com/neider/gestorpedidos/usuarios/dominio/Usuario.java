package com.neider.gestorpedidos.usuarios.dominio;

import java.util.UUID;

/**
 * Perfil del usuario almacenado en {@code public.usuarios}.
 *
 * <p>No contiene credenciales: la contrasena pertenece a Supabase Authentication.</p>
 */
public record Usuario(UUID id, String username, String nombre, String email) {

    public Usuario {
        if (id == null) {
            throw new UsuarioInvalidoException("El identificador del usuario es obligatorio.");
        }
        username = textoObligatorio(username, "username");
        nombre = textoObligatorio(nombre, "nombre");
        email = textoObligatorio(email, "email");
    }

    /**
     * Devuelve una nueva instancia con el perfil actualizado.
     *
     * <p>El identificador y el correo se conservan: el correo pertenece a la
     * identidad administrada por Supabase Authentication.</p>
     */
    public Usuario actualizarPerfil(String nuevoUsername, String nuevoNombre) {
        return new Usuario(id, nuevoUsername, nuevoNombre, email);
    }

    private static String textoObligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new UsuarioInvalidoException("El campo " + campo + " es obligatorio.");
        }
        return valor;
    }
}
