package com.neider.gestorpedidos.usuarios.aplicacion;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.usuarios.aplicacion.caso.ListarPerfilPropioCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.RepositorioUsuariosEnMemoria;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

class ListarPerfilPropioCasoUsoTest {

    private RepositorioUsuariosEnMemoria repositorio;
    private ListarPerfilPropioCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioUsuariosEnMemoria();
        casoUso = new ListarPerfilPropioCasoUso(repositorio,
                new IdentidadFija(UsuariosDePrueba.identidad()));
    }

    @Test
    void listaElPerfilPropioCuandoExiste() {
        Usuario perfil = UsuariosDePrueba.perfil();
        repositorio.sembrar(perfil);

        List<Usuario> perfiles = casoUso.ejecutar();

        assertThat(perfiles).containsExactly(perfil);
    }

    @Test
    void devuelveListaVaciaCuandoNoExisteElPerfil() {
        assertThat(casoUso.ejecutar()).isEmpty();
    }

    @Test
    void noListaElPerfilDeOtroUsuario() {
        repositorio.sembrar(UsuariosDePrueba.perfil(OTRO_ID, "otro", "Otro",
                OTRO_EMAIL));

        assertThat(casoUso.ejecutar()).isEmpty();
    }

    @Test
    void devuelveComoMaximoUnElemento() {
        repositorio.sembrar(UsuariosDePrueba.perfil());

        List<Usuario> perfiles = casoUso.ejecutar();

        assertThat(perfiles).hasSize(1);
        assertThat(perfiles.get(0).id()).isEqualTo(ID);
    }

    @Test
    void exigeIdentidadAutenticada() {
        ListarPerfilPropioCasoUso sinSesion = new ListarPerfilPropioCasoUso(
                repositorio, new IdentidadFija(null));

        assertThatThrownBy(sinSesion::ejecutar)
                .isInstanceOf(UsuarioNoAutenticadoException.class);
    }
}
