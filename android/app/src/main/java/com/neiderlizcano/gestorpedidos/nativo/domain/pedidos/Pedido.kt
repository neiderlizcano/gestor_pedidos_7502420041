package com.neiderlizcano.gestorpedidos.nativo.domain.pedidos

import java.math.BigDecimal
import java.time.LocalDate

/** Pedido con sus 13 atributos académicos; el identificador lo asigna el backend. */
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

/** Campos editables de un pedido para crearlo o actualizarlo; el backend determina la identidad y el propietario. */
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
