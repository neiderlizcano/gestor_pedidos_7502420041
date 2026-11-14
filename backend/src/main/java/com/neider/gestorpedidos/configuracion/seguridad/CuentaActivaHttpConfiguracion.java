package com.neider.gestorpedidos.configuracion.seguridad;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.http.VerificarCuentaActivaHttp;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CuentaActivaHttpConfiguracion {
    @Bean(destroyMethod = "close")
    CloseableHttpClient clienteCuentaActiva() {
        var conexiones = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofSeconds(3)).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))
                        .setSocketTimeout(Timeout.ofSeconds(5)).build())
                .setMaxConnTotal(10).setMaxConnPerRoute(10).build();
        return HttpClients.custom().setConnectionManager(conexiones)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                        .setResponseTimeout(Timeout.ofSeconds(5)).build())
                .disableAutomaticRetries().disableRedirectHandling()
                .disableCookieManagement().disableAuthCaching().disableContentCompression().build();
    }

    @Bean
    VerificarCuentaActiva verificarCuentaActiva(@Qualifier("clienteCuentaActiva") CloseableHttpClient clienteCuentaActiva,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emisor,
            @Value("${seguridad.cuenta.publishable-key:${cuenta.publishable-key:}}") String publishableKey) {
        return new VerificarCuentaActivaHttp(clienteCuentaActiva, emisor, publishableKey);
    }
}
