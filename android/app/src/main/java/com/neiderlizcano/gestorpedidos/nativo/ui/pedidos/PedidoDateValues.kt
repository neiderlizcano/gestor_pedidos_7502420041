package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Material DatePicker representa días mediante medianoche UTC, independiente del huso del dispositivo.
internal fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun pickerDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** Texto del primer elemento del selector: invita a elegir y NO es un estado válido. */
internal const val ESTADO_PLACEHOLDER = "Seleccione un estado"

/** Estados válidos, en este orden (el mismo catálogo de la versión Kodular). La lista es cerrada. */
internal val ESTADOS_PEDIDO: List<String> = listOf("Pendiente", "En preparación", "Enviado", "Entregado", "Cancelado")

/** Opciones exactas del selector: el marcador de posición seguido de los cinco estados. */
internal val OPCIONES_ESTADO: List<String> = listOf(ESTADO_PLACEHOLDER) + ESTADOS_PEDIDO
