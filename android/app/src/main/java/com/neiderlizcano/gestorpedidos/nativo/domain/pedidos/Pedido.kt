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

// Only editable fields; identity and ownership are determined by the backend.
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
