package com.neider.gestorpedidos.usuarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UsuarioTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void creaPerfilValido() {
        Usuario usuario = new Usuario(ID, "nlizcano", "Neider Lizcano", "neider@example.com");

        assertThat(usuario.id()).isEqualTo(ID);
        assertThat(usuario.username()).isEqualTo("nlizcano");
        assertThat(usuario.nombre()).isEqualTo("Neider Lizcano");
        assertThat(usuario.email()).isEqualTo("neider@example.com");
    }

    @Test
    void rechazaIdentificadorNulo() {
        assertThatThrownBy(() -> new Usuario(null, "nlizcano", "Neider Lizcano", "neider@example.com"))
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining("identificador");
    }

    static Stream<Arguments> camposObligatorios() {
        return Stream.of(
                arguments("username", (BiConsumer<UsuarioBuilder, String>) UsuarioBuilder::username),
                arguments("nombre", (BiConsumer<UsuarioBuilder, String>) UsuarioBuilder::nombre),
                arguments("email", (BiConsumer<UsuarioBuilder, String>) UsuarioBuilder::email));
    }

    @ParameterizedTest(name = "rechaza {0} nulo o en blanco")
    @MethodSource("camposObligatorios")
    void rechazaCamposObligatorios(String campo, BiConsumer<UsuarioBuilder, String> setter) {
        UsuarioBuilder nulo = new UsuarioBuilder();
        setter.accept(nulo, null);
        assertThatThrownBy(nulo::crear)
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining(campo);

        UsuarioBuilder blanco = new UsuarioBuilder();
        setter.accept(blanco, "   ");
        assertThatThrownBy(blanco::crear)
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining(campo);
    }

    @Test
    void actualizarPerfilConservaIdentificadorYCorreo() {
        Usuario original = new Usuario(ID, "nlizcano", "Neider Lizcano", "neider@example.com");

        Usuario actualizado = original.actualizarPerfil("nuevo_usuario", "Otro Nombre");

        assertThat(actualizado.id()).isEqualTo(ID);
        assertThat(actualizado.email()).isEqualTo("neider@example.com");
        assertThat(actualizado.username()).isEqualTo("nuevo_usuario");
        assertThat(actualizado.nombre()).isEqualTo("Otro Nombre");
        assertThat(original.username()).isEqualTo("nlizcano");
    }

    @Test
    void actualizarPerfilValidaLosNuevosDatos() {
        Usuario original = new Usuario(ID, "nlizcano", "Neider Lizcano", "neider@example.com");

        assertThatThrownBy(() -> original.actualizarPerfil("  ", "Otro Nombre"))
                .isInstanceOf(UsuarioInvalidoException.class)
                .hasMessageContaining("username");
    }

    private static final class UsuarioBuilder {

        private UUID id = ID;
        private String username = "nlizcano";
        private String nombre = "Neider Lizcano";
        private String email = "neider@example.com";

        void username(String valor) {
            this.username = valor;
        }

        void nombre(String valor) {
            this.nombre = valor;
        }

        void email(String valor) {
            this.email = valor;
        }

        Usuario crear() {
            return new Usuario(id, username, nombre, email);
        }
    }
}
