package com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida;

import java.util.UUID;

/** Verifica la cuenta de Auth, no la existencia del perfil de negocio. Sin cache. */
public interface VerificarCuentaActiva {
    void verificar(UUID autenticado);
}
