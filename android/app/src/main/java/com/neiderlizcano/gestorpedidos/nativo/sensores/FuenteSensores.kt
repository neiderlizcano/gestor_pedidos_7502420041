package com.neiderlizcano.gestorpedidos.nativo.sensores

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Convierte las escuchas del hardware en flujos fríos. El sensor solo permanece registrado mientras
 * alguien recolecta el flujo; al cancelarse la recolección se libera siempre (`awaitClose`).
 */
class FuenteSensores(private val hardware: SensorHardware) {

    fun acelerometro(): Flow<EventoSensor<LecturaAcelerometro>> =
        flujo(TipoSensor.ACELEROMETRO) { valores, precision, instante ->
            if (valores.size < 3 || !valores.take(3).all { it.isFinite() }) EventoSensor.DatosNoDisponibles
            else EventoSensor.Lectura(LecturaAcelerometro(valores[0], valores[1], valores[2], instante, precision <= PRECISION_BAJA))
        }

    fun proximidad(): Flow<EventoSensor<LecturaProximidad>> {
        // El rango máximo es una propiedad del sensor y se consulta una sola vez por suscripción.
        val rango = hardware.informacion(TipoSensor.PROXIMIDAD)?.rangoMaximo
        return flujo(TipoSensor.PROXIMIDAD) { valores, _, _ ->
            val distancia = valores.firstOrNull()
            if (distancia == null || !distancia.isFinite() || distancia < 0f || rango == null || !rango.isFinite() || rango <= 0f)
                EventoSensor.DatosNoDisponibles
            else EventoSensor.Lectura(LecturaProximidad(distancia, rango))
        }
    }

    private fun <T> flujo(
        tipo: TipoSensor,
        convertir: (FloatArray, Int, Long) -> EventoSensor<T>
    ): Flow<EventoSensor<T>> = callbackFlow {
        if (hardware.informacion(tipo) == null) {
            trySend(EventoSensor.NoDisponible)
            // Sin sensor no hay nada que liberar; el flujo se mantiene abierto hasta que cancelen.
            awaitClose { }
            return@callbackFlow
        }
        val escucha = EscuchaSensor { valores, precision, instante ->
            val evento = try { convertir(valores, precision, instante) } catch (_: RuntimeException) {
                EventoSensor.Error(MotivoErrorSensor.FALLO_INESPERADO)
            }
            trySend(evento)
        }
        val registrado = try { hardware.registrar(tipo, escucha) } catch (_: RuntimeException) { false }
        if (!registrado) {
            hardware.liberar(escucha)
            trySend(EventoSensor.Error(MotivoErrorSensor.REGISTRO_RECHAZADO))
        }
        awaitClose { hardware.liberar(escucha) }
        // Solo importa la lectura más reciente: se descartan las antiguas si la interfaz va más lenta.
    }.buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        /** SensorManager.SENSOR_STATUS_ACCURACY_LOW = 1; 0 es "no confiable". */
        const val PRECISION_BAJA = 1
    }
}
