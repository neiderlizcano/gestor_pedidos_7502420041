package com.neider.gestorpedidos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://supabase.invalid/auth/v1",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://supabase.invalid/auth/v1/.well-known/jwks.json"
})
class GestorPedidosApplicationTests {

    @Test
    void contextLoads() {
    }
}
