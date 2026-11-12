package com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida;

import java.util.UUID;

/** Identidad confiable sin dependencia del correo o de la existencia del perfil. */
public interface IdentidadCuentaAutenticada {
    UUID usuarioActual();
}
