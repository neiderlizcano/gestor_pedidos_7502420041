package com.neiderlizcano.gestorpedidos.nativo.sensores

// Abstracción mínima sobre SensorManager. Permite probar la lógica sin hardware y verificar que cada
// suscripción se libera. No depende de Compose ni de ViewModels.

/** Sensores que usa el módulo. */
enum class TipoSensor { ACELEROMETRO, PROXIMIDAD }

/** Características del sensor físico o virtual disponible en el dispositivo. */
data class InfoSensor(val nombre: String, val rangoMaximo: Float)

/** Receptor de lecturas crudas. `valores` es una copia propia: no se reutiliza el arreglo del sistema. */
fun interface EscuchaSensor {
    fun alRecibir(valores: FloatArray, precision: Int, instanteNs: Long)
}

interface SensorHardware {
    /** Devuelve null si el dispositivo no tiene ese sensor. */
    fun informacion(tipo: TipoSensor): InfoSensor?

    /** Registra la escucha; devuelve false si el sistema rechaza la suscripción. */
    fun registrar(tipo: TipoSensor, escucha: EscuchaSensor): Boolean

    /** Libera la escucha. Es idempotente: liberar una escucha desconocida no hace nada. */
    fun liberar(escucha: EscuchaSensor)
}

/** Motivos por los que un sensor existente no entrega datos utilizables. */
enum class MotivoErrorSensor { REGISTRO_RECHAZADO, FALLO_INESPERADO }

/** Lo que emite un flujo de sensor: nunca lanza excepciones hacia la interfaz. */
sealed interface EventoSensor<out T> {
    data object NoDisponible : EventoSensor<Nothing>
    /** El sensor existe pero entregó valores vacíos o no finitos. */
    data object DatosNoDisponibles : EventoSensor<Nothing>
    data class Error(val motivo: MotivoErrorSensor) : EventoSensor<Nothing>
    data class Lectura<T>(val valor: T) : EventoSensor<T>
}

/** Aceleración en m/s² incluyendo gravedad, tal como la entrega el sensor. */
data class LecturaAcelerometro(val x: Float, val y: Float, val z: Float, val instanteNs: Long, val precisionBaja: Boolean)

/** Proximidad: `distanciaCm` es la medida cruda y `rangoMaximoCm` el máximo que reporta el sensor. */
data class LecturaProximidad(val distanciaCm: Float, val rangoMaximoCm: Float) {
    /** Muchos sensores son binarios (solo dan 0 y el máximo, típicamente 5 cm o menos). */
    val esBinario: Boolean get() = rangoMaximoCm <= UMBRAL_CERCA_CM
    /** Cerca si la distancia está por debajo del máximo del sensor y dentro de 5 cm. */
    val cerca: Boolean get() = distanciaCm < minOf(rangoMaximoCm, UMBRAL_CERCA_CM)

    companion object { const val UMBRAL_CERCA_CM = 5f }
}
