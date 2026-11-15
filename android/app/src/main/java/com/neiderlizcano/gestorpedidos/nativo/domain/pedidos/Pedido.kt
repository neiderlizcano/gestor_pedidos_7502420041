package com.neiderlizcano.gestorpedidos.nativo.domain.pedidos

import java.math.BigDecimal
import java.time.LocalDate

data class Pedido(
    val id: Long,
    val fecha: LocalDate,
    val fechaEnvio: LocalDate,
    val fechaEntrega: LocalDate,
    val cliente: String,
    val proveedor: String,
    val valor: BigDecimal,
    val estado: String,
    val pais: String,
    val departamento: String,
    val ciudad: String,
    val nomenclaturaVivienda: String,
    val propina: BigDecimal
)

// Solo contiene campos editables; el backend determina la identidad y el propietario.
data class DatosPedido(
    val fecha: LocalDate,
    val fechaEnvio: LocalDate,
    val fechaEntrega: LocalDate,
    val cliente: String,
    val proveedor: String,
    val valor: BigDecimal,
    val estado: String,
    val pais: String,
    val departamento: String,
    val ciudad: String,
    val nomenclaturaVivienda: String,
    val propina: BigDecimal
)
