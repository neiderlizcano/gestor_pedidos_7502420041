package com.neider.gestorpedidos.configuracion.persistencia;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** Infraestructura optativa: construir el DataSource no abre conexiones. */
@Configuration(proxyBeanMethods = false)
@Profile("persistencia")
public class PersistenciaConfiguracion {

    @Bean
    JdbcTransactionManager jdbcTransactionManager(HikariDataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        if (url.isBlank() || !url.startsWith("jdbc:postgresql://")
                || username.isBlank() || password.isBlank()) {
            // Nunca incluir valores de conexion en mensajes de error.
            throw new IllegalArgumentException(
                    "El perfil persistencia requiere DB_URL PostgreSQL, DB_USERNAME y DB_PASSWORD no vacios.");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
