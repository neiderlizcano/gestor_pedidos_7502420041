package com.neider.gestorpedidos.usuarios.aplicacion;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neider.gestorpedidos.usuarios.aplicacion.caso.ConsultarPerfilPropioCasoUso;
import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilNoEncontradoException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.IdentidadFija;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.RepositorioUsuariosEnMemoria;
import com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

class ConsultarPerfilPropioCasoUsoTest {

    private RepositorioUsuariosEnMemoria repositorio;
    private ConsultarPerfilPropioCasoUso casoUso;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioUsuariosEnMemoria();
        casoUso = new ConsultarPerfilPropioCasoUso(repositorio,
                new IdentidadFija(UsuariosDePrueba.identidad()));
    }

    @Test
    void consultaElPerfilPropio() {
        Usuario perfil = UsuariosDePrueba.perfil();
        repositorio.sembrar(perfil);

        Usuario encontrado = casoUso.ejecutar();

        assertThat(encontrado).isEqualTo(perfil);
    }

    @Test
    void rechazaPerfilInexistente() {
        assertThatThrownBy(casoUso::ejecutar)
                .isInstanceOf(PerfilNoEncontradoException.class);
    }

    @Test
    void noConsultaElPerfilDeOtroUsuario() {
        repositorio.sembrar(UsuariosDePrueba.perfil(OTRO_ID, "otro", "Otro",
                OTRO_EMAIL));

        assertThatThrownBy(casoUso::ejecutar)
                .isInstanceOf(PerfilNoEncontradoException.class);
    }

    @Test
    void cadaIdentidadConsultaSuPropioPerfil() {
        repositorio.sembrar(UsuariosDePrueba.perfil());
        repositorio.sembrar(UsuariosDePrueba.perfil(OTRO_ID, "otro", "Otro",
                OTRO_EMAIL));
        ConsultarPerfilPropioCasoUso otroCaso = new ConsultarPerfilPropioCasoUso(
                repositorio, new IdentidadFija(OTRO_ID, OTRO_EMAIL));

        assertThat(casoUso.ejecutar().id()).isEqualTo(ID);
        assertThat(otroCaso.ejecutar().id()).isEqualTo(OTRO_ID);
    }

    @Test
    void exigeIdentidadAutenticada() {
        ConsultarPerfilPropioCasoUso sinSesion = new ConsultarPerfilPropioCasoUso(
                repositorio, new IdentidadFija(null));

        assertThatThrownBy(sinSesion::ejecutar)
                .isInstanceOf(UsuarioNoAutenticadoException.class);
    }
}
