package com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada;

/**
 * Datos editables del perfil de usuario.
 *
 * <p>No contiene identificador ni correo: ambos provienen del puerto de
 * identidad autenticada.</p>
 */
public record DatosPerfilComando(String username, String nombre) {
}
