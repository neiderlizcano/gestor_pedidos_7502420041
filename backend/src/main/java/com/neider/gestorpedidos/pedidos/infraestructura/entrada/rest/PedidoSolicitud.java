package com.neider.gestorpedidos.pedidos.infraestructura.entrada.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.DatosPedidoComando;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Solo datos editables. Identificador y propietario del cliente nunca se utilizan. */
@JsonIgnoreProperties({"id", "usuarioId", "usuario_id"})
public record PedidoSolicitud(
        @NotNull LocalDate fecha,
        @NotNull LocalDate fechaEnvio,
        @NotNull LocalDate fechaEntrega,
        @NotBlank String cliente,
        @NotBlank String proveedor,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal valor,
        @NotBlank String estado,
        @NotBlank String pais,
        @NotBlank String departamento,
        @NotBlank String ciudad,
        @NotBlank String nomenclaturaVivienda,
        @NotNull @DecimalMin("0") BigDecimal propina) {

    DatosPedidoComando aComando() {
        return new DatosPedidoComando(fecha, fechaEnvio, fechaEntrega, cliente, proveedor,
                valor, estado, pais, departamento, ciudad, nomenclaturaVivienda, propina);
    }
}
