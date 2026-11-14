package com.neider.gestorpedidos.configuracion.usuarios;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.http.EliminarCuentaHttp;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("persistencia")
public class EliminarCuentaHttpConfiguracion {
    @Bean(destroyMethod = "close")
    CloseableHttpClient clienteEliminarCuenta() {
        var conexiones = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofSeconds(3)).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))
                        .setSocketTimeout(Timeout.ofSeconds(8)).build())
                .setMaxConnTotal(10).setMaxConnPerRoute(10).build();
        return HttpClients.custom().setConnectionManager(conexiones)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                        .setResponseTimeout(Timeout.ofSeconds(8)).build())
                .disableAutomaticRetries().disableRedirectHandling()
                .disableCookieManagement().disableAuthCaching().disableContentCompression()
                .build();
    }

    @Bean
    EliminarCuenta eliminarCuentaHttp(@Qualifier("clienteEliminarCuenta") CloseableHttpClient clienteEliminarCuenta,
            @Value("${cuenta.edge-function-url}") String url,
            @Value("${cuenta.publishable-key:}") String publishableKey) {
        return new EliminarCuentaHttp(clienteEliminarCuenta, url, publishableKey);
    }
}
