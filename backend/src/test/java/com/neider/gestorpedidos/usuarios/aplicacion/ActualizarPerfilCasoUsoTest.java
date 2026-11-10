package com.neider.gestorpedidos.usuarios.aplicacion;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.usuarios.aplicacion.caso.ActualizarPerfilCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilNoEncontradoException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.DatosPerfilComando;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.RepositorioUsuariosEnMemoria;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.neider.gestorpedidos.usuarios.dominio.UsuarioInvalidoException;

class ActualizarPerfilCasoUsoTest {

    private RepositorioUsuariosEnMemoria repositorio;
    private ActualizarPerfilCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioUsuariosEnMemoria();
        casoUso = new ActualizarPerfilCasoUso(repositorio,
                new IdentidadFija(UsuariosDePrueba.identidad()));
    }

    @Test
    void actualizaSoloUsernameYNombreYConservaIdYCorreo() {
        repositorio.sembrar(UsuariosDePrueba.perfil());

        Usuario actualizado = casoUso.ejecutar(
                UsuariosDePrueba.comando("nuevo_usuario", "Nuevo Nombre"));

        assertThat(actualizado.id()).isEqualTo(ID);
        assertThat(actualizado.email()).isEqualTo(EMAIL);
        assertThat(actualizado.username()).isEqualTo("nuevo_usuario");
        assertThat(actualizado.nombre()).isEqualTo("Nuevo Nombre");
        assertThat(repositorio.buscarPorId(ID)).contains(actualizado);
    }

    @Test
    void rechazaPerfilInexistente() {
        assertThatThrownBy(() -> casoUso.ejecutar(UsuariosDePrueba.comando()))
                .isInstanceOf(PerfilNoEncontradoException.class);
    }

    @Test
    void noActualizaElPerfilDeOtroUsuario() {
        Usuario existente = UsuariosDePrueba.perfil();
        repositorio.sembrar(existente);
        ActualizarPerfilCasoUso otroCaso = new ActualizarPerfilCasoUso(repositorio,
                new IdentidadFija(OTRO_ID, OTRO_EMAIL));

        assertThatThrownBy(() -> otroCaso.ejecutar(
                UsuariosDePrueba.comando("intruso", "Intruso")))
                .isInstanceOf(PerfilNoEncontradoException.class);

        assertThat(repositorio.buscarPorId(ID)).contains(existente);
    }

    @Test
    void reutilizaLasValidacionesDelDominio() {
        Usuario existente = UsuariosDePrueba.perfil();
        repositorio.sembrar(existente);
        DatosPerfilComando invalido = new DatosPerfilComando("   ", "Nuevo Nombre");

        assertThatThrownBy(() -> casoUso.ejecutar(invalido))
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining("username");

        assertThat(repositorio.buscarPorId(ID)).contains(existente);
    }

    @Test
    void rechazaComandoNulo() {
        assertThatThrownBy(() -> casoUso.ejecutar(null))
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining("datos del perfil");
    }

    @Test
    void exigeIdentidadAutenticada() {
        ActualizarPerfilCasoUso sinSesion = new ActualizarPerfilCasoUso(repositorio,
                new IdentidadFija(null));

        assertThatThrownBy(() -> sinSesion.ejecutar(UsuariosDePrueba.comando()))
                .isInstanceOf(UsuarioNoAutenticadoException.class);
    }
}
