package com.neider.gestorpedidos.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.caso.EliminarCuentaPropiaCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadCuentaAutenticada;
import org.junit.jupiter.api.Test;

class EliminarCuentaPropiaCasoUsoTest {
    private final UUID a = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final EliminarCuenta cuentas = mock(EliminarCuenta.class);
    private final IdentidadCuentaAutenticada identidad = mock(IdentidadCuentaAutenticada.class);
    private final EliminarCuentaPropiaCasoUso caso = new EliminarCuentaPropiaCasoUso(cuentas, identidad);

    @Test
    void eliminaSoloIdentidadSinConsultarPerfilNiCorreo() {
        when(identidad.usuarioActual()).thenReturn(a);
        caso.ejecutar();
        verify(identidad).usuarioActual();
        verify(cuentas).eliminar(a);
        verifyNoMoreInteractions(identidad, cuentas);
    }

    @Test
    void sinIdentidadNoInvocaElPuerto() {
        assertThatThrownBy(caso::ejecutar).isInstanceOf(UsuarioNoAutenticadoException.class);
        verifyNoInteractions(cuentas);
    }

    @Test
    void propagaFalloSinConfirmarExito() {
        when(identidad.usuarioActual()).thenReturn(a);
        RuntimeException fallo = new IllegalStateException("fallo sintetico");
        doThrow(fallo).when(cuentas).eliminar(a);
        assertThatThrownBy(caso::ejecutar).isSameAs(fallo);
    }

    @Test
    void exigeDependencias() {
        assertThatThrownBy(() -> new EliminarCuentaPropiaCasoUso(null, identidad))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EliminarCuentaPropiaCasoUso(cuentas, null))
                .isInstanceOf(NullPointerException.class);
    }
}
