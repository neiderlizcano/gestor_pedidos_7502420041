package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.*

@OptIn(ExperimentalCoroutinesApi::class)
class ConsultaGeocodificacionTest {
    private val exito = ResultadoGeocodificacion.Exito(DireccionCruda(calle = "Calle 12", numero = "2"))

    @Test fun sinRespuestaTerminaAlLimiteYDescartaLaRespuestaPosterior() = runTest {
        lateinit var responder: (ResultadoGeocodificacion) -> Unit
        var cancelaciones = 0
        val consulta = async {
            esperarGeocodificacion(1_000) { callback -> responder = callback; { cancelaciones++ } }
        }
        runCurrent(); advanceTimeBy(999); runCurrent(); assertFalse(consulta.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertEquals(ResultadoGeocodificacion.TiempoAgotado, consulta.await())
        assertEquals(1, cancelaciones)
        responder(exito); runCurrent()
        assertEquals(ResultadoGeocodificacion.TiempoAgotado, consulta.await())
    }

    @Test fun cancelarDesdeFueraNoSeConvierteEnTimeoutYPermiteReintentar() = runTest {
        lateinit var responder: (ResultadoGeocodificacion) -> Unit
        var cancelada = false
        val consulta = async {
            esperarGeocodificacion(1_000) { callback -> responder = callback; { cancelada = true } }
        }
        runCurrent(); consulta.cancelAndJoin()
        assertTrue(consulta.isCancelled); assertTrue(cancelada)
        responder(exito)
        assertEquals(exito, esperarGeocodificacion(1_000) { callback -> callback(exito); {} })
    }

    @Test fun unTimeoutExternoConservaSuCancelacion() = runTest {
        try {
            withTimeout(10) { esperarGeocodificacion(1_000) { {} } }
            fail("El plazo externo debe propagarse")
        } catch (_: TimeoutCancellationException) { /* Cancelación esperada del llamador. */ }
    }

    @Test fun callbacksDuplicadosNoReanudanDosVecesNiCambianElResultado() = runTest {
        val resultado = esperarGeocodificacion(1_000) { responder ->
            responder(exito); responder(ResultadoGeocodificacion.Fallo); responder(exito);
            {}
        }
        assertEquals(exito, resultado)
    }

    @Test fun ioYErroresGenericosNoDemuestranFaltaDeConexion() = runTest {
        for (error in listOf(IOException("dato simulado"), IllegalArgumentException(), SecurityException())) {
            assertEquals(ResultadoGeocodificacion.Fallo, esperarGeocodificacion(1_000) { throw error })
        }
        assertEquals(ResultadoGeocodificacion.Fallo, esperarGeocodificacion(1_000) { responder ->
            // El adaptador usa este mismo resultado para GeocodeListener.onError; no conserva su mensaje.
            responder(ResultadoGeocodificacion.Fallo); {}
        })
    }

    @Test fun erroresDeApiBloqueanteYRechazoDeTrabajoNoDejanLaUiEsperando() = runTest {
        assertEquals(ResultadoGeocodificacion.Fallo, esperarGeocodificacion(1_000) { responder ->
            consultarGeocodificacionBloqueante(Executor { it.run() }, { throw IOException() }, responder)
        })
        assertEquals(ResultadoGeocodificacion.Fallo, esperarGeocodificacion(1_000) { responder ->
            consultarGeocodificacionBloqueante(Executor { throw RejectedExecutionException() }, { exito }, responder)
        })
    }

    @Test fun apiAntiguaQueIgnoraInterrupcionesNoRetieneLaEsperaNiAcumulaTrabajos() = runTest {
        val inicio = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val fin = CountDownLatch(1)
        val ejecutor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, SynchronousQueue(),
            { tarea -> Thread(tarea).apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
        try {
            val primera = async {
                esperarGeocodificacion(1_000) { responder ->
                    consultarGeocodificacionBloqueante(ejecutor, {
                        inicio.countDown()
                        while (liberar.count > 0) {
                            try { liberar.await() } catch (_: InterruptedException) { /* Simula la API nativa. */ }
                        }
                        fin.countDown(); exito
                    }, responder)
                }
            }
            runCurrent(); assertTrue(inicio.await(2, TimeUnit.SECONDS))
            advanceTimeBy(1_000); runCurrent()
            assertEquals(ResultadoGeocodificacion.TiempoAgotado, primera.await())
            val segunda = esperarGeocodificacion(1_000) { responder ->
                consultarGeocodificacionBloqueante(ejecutor, { exito }, responder)
            }
            assertEquals(ResultadoGeocodificacion.Fallo, segunda)
            assertEquals(0, ejecutor.queue.size); assertEquals(1, ejecutor.poolSize)
            liberar.countDown(); assertTrue(fin.await(2, TimeUnit.SECONDS))
            assertEquals(ResultadoGeocodificacion.TiempoAgotado, primera.await())
        } finally { liberar.countDown(); ejecutor.shutdownNow() }
    }

    @Test fun timeoutDelGeocodificadorLlegaAlViewModelYElReintentoEsExplicito() = runTest {
        var llamadas = 0
        var responde = false
        val proveedor = object : ProveedorPosicion {
            override suspend fun obtenerActual(precisa: Boolean) = ResultadoPosicion.Exito(Posicion(0.0, 0.0))
        }
        val geocodificador = object : GeocodificadorInverso {
            override suspend fun resolver(posicion: Posicion): ResultadoGeocodificacion {
                llamadas++
                return esperarGeocodificacion(1_000) { callback -> if (responde) callback(exito); {} }
            }
        }
        val vm = UbicacionPedidoViewModel(proveedor, geocodificador, backgroundScope)
        vm.iniciar(false); runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertEquals(EstadoUbicacion.Error(ErrorUbicacion.TIEMPO_AGOTADO), vm.uiState.value)
        assertFalse(vm.ocupado); assertEquals(1, llamadas)
        responde = true; vm.iniciar(false); runCurrent()
        assertTrue(vm.uiState.value is EstadoUbicacion.Completado); assertEquals(2, llamadas)
    }
}
