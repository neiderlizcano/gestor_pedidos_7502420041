package com.neider.gestorpedidos.usuarios.aplicacion;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.usuarios.aplicacion.caso.CrearPerfilCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilConflictoException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.DatosPerfilComando;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.RepositorioUsuariosEnMemoria;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.neider.gestorpedidos.usuarios.dominio.UsuarioInvalidoException;

class CrearPerfilCasoUsoTest {

    private RepositorioUsuariosEnMemoria repositorio;
    private IdentidadFija identidad;
    private CrearPerfilCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioUsuariosEnMemoria();
        identidad = new IdentidadFija(UsuariosDePrueba.identidad());
        casoUso = new CrearPerfilCasoUso(repositorio, identidad);
    }

    @Test
    void creaPerfilConIdentidadYComando() {
        Usuario creado = casoUso.ejecutar(UsuariosDePrueba.comando());

        assertThat(creado.id()).isEqualTo(ID);
        assertThat(creado.email()).isEqualTo(EMAIL);
        assertThat(creado.username()).isEqualTo("nlizcano");
        assertThat(creado.nombre()).isEqualTo("Neider Lizcano");
        assertThat(repositorio.buscarPorId(ID)).contains(creado);
        assertThat(identidad.llamadas()).isEqualTo(1);
    }

    @Test
    void tomaIdYCorreoExclusivamenteDeLaIdentidad() {
        CrearPerfilCasoUso otroCaso = new CrearPerfilCasoUso(repositorio,
                new IdentidadFija(OTRO_ID, OTRO_EMAIL));

        Usuario creado = otroCaso.ejecutar(UsuariosDePrueba.comando());

        assertThat(creado.id()).isEqualTo(OTRO_ID);
        assertThat(creado.email()).isEqualTo(OTRO_EMAIL);
        assertThat(creado.id()).isNotEqualTo(ID);
        assertThat(creado.email()).isNotEqualTo(EMAIL);
    }

    @Test
    void noSobrescribeUnPerfilExistente() {
        Usuario existente = UsuariosDePrueba.perfil();
        repositorio.sembrar(existente);

        assertThatThrownBy(() -> casoUso.ejecutar(
                UsuariosDePrueba.comando("otro_usuario", "Otro Nombre")))
                .isInstanceOf(PerfilConflictoException.class);

        assertThat(repositorio.buscarPorId(ID)).contains(existente);
        assertThat(repositorio.total()).isEqualTo(1);
    }

    @Test
    void rechazaUnCorreoYaRegistrado() {
        repositorio.sembrar(UsuariosDePrueba.perfil(OTRO_ID, "otro", "Otro", EMAIL));

        assertThatThrownBy(() -> casoUso.ejecutar(UsuariosDePrueba.comando()))
                .isInstanceOf(PerfilConflictoException.class);

        assertThat(repositorio.total()).isEqualTo(1);
    }

    @Test
    void reutilizaLasValidacionesDelDominio() {
        DatosPerfilComando invalido = new DatosPerfilComando("   ", "Neider Lizcano");

        assertThatThrownBy(() -> casoUso.ejecutar(invalido))
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining("username");

        assertThat(repositorio.total()).isZero();
    }

    @Test
    void rechazaComandoNulo() {
        assertThatThrownBy(() -> casoUso.ejecutar(null))
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining("datos del perfil");

        assertThat(repositorio.total()).isZero();
    }

    @Test
    void exigeIdentidadAutenticada() {
        CrearPerfilCasoUso sinSesion = new CrearPerfilCasoUso(repositorio,
                new IdentidadFija(null));

        assertThatThrownBy(() -> sinSesion.ejecutar(UsuariosDePrueba.comando()))
                .isInstanceOf(UsuarioNoAutenticadoException.class);

        assertThat(repositorio.total()).isZero();
    }
}
