package com.neider.gestorpedidos.usuarios.aplicacion.caso;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.EliminarCuentaPropia;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadCuentaAutenticada;

public final class EliminarCuentaPropiaCasoUso implements EliminarCuentaPropia {
    private final EliminarCuenta cuentas;
    private final IdentidadCuentaAutenticada identidad;

    public EliminarCuentaPropiaCasoUso(EliminarCuenta cuentas, IdentidadCuentaAutenticada identidad) {
        this.cuentas = Objects.requireNonNull(cuentas, "El puerto de eliminacion es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad, "La identidad es obligatoria.");
    }

    @Override
    public void ejecutar() {
        UUID id = identidad.usuarioActual();
        if (id == null) {
            throw new UsuarioNoAutenticadoException();
        }
        cuentas.eliminar(id);
    }
}
