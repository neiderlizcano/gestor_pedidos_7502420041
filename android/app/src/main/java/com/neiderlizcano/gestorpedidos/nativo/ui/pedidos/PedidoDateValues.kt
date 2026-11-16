package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Material DatePicker representa días mediante medianoche UTC, independiente del huso del dispositivo.
internal fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun pickerDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** Texto del primer elemento del selector: invita a elegir y NO es un estado válido. */
internal const val ESTADO_PLACEHOLDER = "Seleccione un estado"

/** Opciones de creación, en el orden acordado. Spring también puede devolver otros estados. */
internal val ESTADOS_PEDIDO: List<String> = listOf("Pendiente", "En preparación", "Enviado", "Entregado", "Cancelado")

/** Opciones exactas del selector: el marcador de posición seguido de los cinco estados. */
internal val OPCIONES_ESTADO: List<String> = listOf(ESTADO_PLACEHOLDER) + ESTADOS_PEDIDO

/** Conserva visible y seleccionable un estado existente sin sustituirlo por una opción del catálogo. */
internal fun opcionesEstado(original: String?): List<String> =
    if (!original.isNullOrBlank() && original !in OPCIONES_ESTADO) OPCIONES_ESTADO + original else OPCIONES_ESTADO
