package com.neiderlizcano.gestorpedidos.nativo.sensores

import kotlin.math.abs
import kotlin.math.sqrt

/** Magnitud del vector de aceleración en m/s² (incluye la gravedad). */
fun LecturaAcelerometro.magnitud(): Float = sqrt(x * x + y * y + z * z)

/**
 * Detecta movimientos significativos comparando la magnitud con la gravedad: en reposo vale ≈ 9,81 m/s²
 * sin importar la orientación, y una sacudida o un golpe la desvían. Una detección nueva exige que haya
 * pasado [enfriamientoNs] desde la anterior para no contar cada muestra de una misma sacudida.
 *
 * Es puro Kotlin y no es seguro entre hilos: se usa desde un único flujo.
 */
class DetectorMovimiento(
    private val umbralMs2: Float = UMBRAL_PREDETERMINADO,
    private val enfriamientoNs: Long = ENFRIAMIENTO_PREDETERMINADO_NS
) {
    /** `movimiento`: la muestra actual supera el umbral. `deteccionNueva`: cuenta como evento distinto. */
    data class Resultado(val desviacion: Float, val movimiento: Boolean, val deteccionNueva: Boolean)

    private var ultimaDeteccionNs: Long? = null

    fun procesar(lectura: LecturaAcelerometro): Resultado {
        val desviacion = abs(lectura.magnitud() - GRAVEDAD_MS2)
        val movimiento = desviacion >= umbralMs2
        val previa = ultimaDeteccionNs
        val nueva = movimiento && (previa == null || lectura.instanteNs - previa >= enfriamientoNs)
        if (nueva) ultimaDeteccionNs = lectura.instanteNs
        return Resultado(desviacion, movimiento, nueva)
    }

    companion object {
        const val GRAVEDAD_MS2 = 9.80665f
        const val UMBRAL_PREDETERMINADO = 3f
        const val ENFRIAMIENTO_PREDETERMINADO_NS = 1_000_000_000L
    }
}
