package com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida;

import java.util.UUID;

public interface EliminarCuenta {
    void eliminar(UUID autenticado);
}
