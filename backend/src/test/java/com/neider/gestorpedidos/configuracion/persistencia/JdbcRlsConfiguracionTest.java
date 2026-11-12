package com.neider.gestorpedidos.configuracion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Valida nombres sinteticos sin leer el entorno ni abrir conexiones. */
class JdbcRlsConfiguracionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "gestor_pedidos_jdbc",
            "gestor_pedidos_jdbc.abcdefghijklmnopqrst",
            "gestor_pedidos_jdbc.abc123def456ghi789jk"
    })
    void aceptaConexionDirectaYSessionPooler(String username) {
        assertThat(JdbcRlsIT.usuarioConexionPermitido(username)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "postgres",
            "postgres.abcdefghijklmnopqrst",
            "service_role.abcdefghijklmnopqrst",
            "authenticated.abcdefghijklmnopqrst",
            "otro_rol.abcdefghijklmnopqrst",
            "gestor_pedidos_jdbc_otro.abcdefghijklmnopqrst",
            "gestor_pedidos_jdbc.",
            "gestor_pedidos_jdbc..abcdefghijklmnopqrst",
            "gestor_pedidos_jdbc.abcdefghijklmnopqrst.extra",
            " gestor_pedidos_jdbc",
            "gestor_pedidos_jdbc.abcdefghijklmnopqrst ",
            "gestor_pedidos_jdbc.abc/def",
            "gestor_pedidos_jdbc.abc?sslmode=disable",
            "gestor_pedidos_jdbc.abc\ndef"
    })
    void rechazaOtrosRolesYSufijosInvalidos(String username) {
        assertThat(JdbcRlsIT.usuarioConexionPermitido(username)).isFalse();
    }
}
