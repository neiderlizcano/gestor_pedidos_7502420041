package com.neider.gestorpedidos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://supabase.invalid/auth/v1",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://supabase.invalid/auth/v1/.well-known/jwks.json"
})
class GestorPedidosApplicationTests {

    @MockitoBean
    private VerificarCuentaActiva cuentas;

    @Test
    void contextLoads() {
    }
}
