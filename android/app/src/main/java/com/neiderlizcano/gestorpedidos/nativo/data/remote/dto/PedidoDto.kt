package com.neiderlizcano.gestorpedidos.nativo.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.time.LocalDate

// Los campos HTTP anulables permiten detectar ausencias y valores null antes de construir el modelo de dominio.
data class PedidoDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("fecha") val fecha: LocalDate?,
    @SerializedName("fechaEnvio") val fechaEnvio: LocalDate?,
    @SerializedName("fechaEntrega") val fechaEntrega: LocalDate?,
    @SerializedName("cliente") val cliente: String?,
    @SerializedName("proveedor") val proveedor: String?,
    @SerializedName("valor") val valor: BigDecimal?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("pais") val pais: String?,
    @SerializedName("departamento") val departamento: String?,
    @SerializedName("ciudad") val ciudad: String?,
    @SerializedName("nomenclaturaVivienda") val nomenclaturaVivienda: String?,
    @SerializedName("propina") val propina: BigDecimal?
)

data class PedidoSolicitudDto(
    @SerializedName("fecha") val fecha: LocalDate,
    @SerializedName("fechaEnvio") val fechaEnvio: LocalDate,
    @SerializedName("fechaEntrega") val fechaEntrega: LocalDate,
    @SerializedName("cliente") val cliente: String,
    @SerializedName("proveedor") val proveedor: String,
    @SerializedName("valor") val valor: BigDecimal,
    @SerializedName("estado") val estado: String,
    @SerializedName("pais") val pais: String,
    @SerializedName("departamento") val departamento: String,
    @SerializedName("ciudad") val ciudad: String,
    @SerializedName("nomenclaturaVivienda") val nomenclaturaVivienda: String,
    @SerializedName("propina") val propina: BigDecimal
)
