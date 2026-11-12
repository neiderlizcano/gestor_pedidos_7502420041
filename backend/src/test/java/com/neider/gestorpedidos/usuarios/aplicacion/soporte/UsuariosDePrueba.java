package com.neider.gestorpedidos.usuarios.aplicacion.soporte;

import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.DatosPerfilComando;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Datos de ejemplo compartidos por las pruebas de la capa de aplicacion.
 */
public final class UsuariosDePrueba {

    public static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTRO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final String EMAIL = "neider@example.com";
    public static final String OTRO_EMAIL = "otro@example.com";

    private UsuariosDePrueba() {
    }

    public static IdentidadUsuario identidad() {
        return new IdentidadUsuario(ID, EMAIL);
    }

    public static IdentidadUsuario identidad(UUID id, String email) {
        return new IdentidadUsuario(id, email);
    }

    public static DatosPerfilComando comando() {
        return new DatosPerfilComando("nlizcano", "Neider Lizcano");
    }

    public static DatosPerfilComando comando(String username, String nombre) {
        return new DatosPerfilComando(username, nombre);
    }

    public static Usuario perfil() {
        return perfil(ID, "nlizcano", "Neider Lizcano", EMAIL);
    }

    public static Usuario perfil(UUID id, String username, String nombre, String email) {
        return new Usuario(id, username, nombre, email);
    }
}
