package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Limita la espera, no convierte cancelaciones externas en errores y descarta callbacks tardíos o repetidos. */
internal suspend fun esperarGeocodificacion(
    tiempoLimiteMs: Long,
    iniciar: (responder: (ResultadoGeocodificacion) -> Unit) -> (() -> Unit)
): ResultadoGeocodificacion {
    require(tiempoLimiteMs > 0)
    return withTimeoutOrNull(tiempoLimiteMs) {
        suspendCancellableCoroutine { continuacion ->
            val terminada = AtomicBoolean(false)
            val responder: (ResultadoGeocodificacion) -> Unit = { resultado ->
                if (terminada.compareAndSet(false, true)) continuacion.resume(resultado)
            }
            try {
                val cancelar = iniciar(responder)
                continuacion.invokeOnCancellation { terminada.set(true); cancelar() }
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (_: Exception) {
                // IOException/onError no permiten demostrar ausencia de Internet. No se conserva el mensaje crudo.
                responder(ResultadoGeocodificacion.Fallo)
            }
        }
    } ?: ResultadoGeocodificacion.TiempoAgotado
}

/**
 * La API antigua bloquea un hilo nativo. La UI deja de esperar aunque Android ignore la interrupción.
 * El ejecutor acotado del adaptador evita acumular hilos o una cola de consultas tras reintentos.
 */
internal fun consultarGeocodificacionBloqueante(
    ejecutor: Executor,
    consulta: () -> ResultadoGeocodificacion,
    responder: (ResultadoGeocodificacion) -> Unit
): () -> Unit {
    val tarea = FutureTask {
        val resultado = try { consulta() } catch (_: Exception) { ResultadoGeocodificacion.Fallo }
        responder(resultado)
    }
    ejecutor.execute(tarea)
    return { tarea.cancel(true); Unit }
}
