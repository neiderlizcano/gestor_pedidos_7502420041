package com.neiderlizcano.gestorpedidos.nativo.sensores

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Pruebas contra el SensorManager real del emulador o dispositivo. Dependen de los sensores que existan:
// si falta el sensor, la prueba se omite (Assume) en lugar de dar un falso positivo.
class AndroidSensorHardwareTest {
    private val contexto: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = contexto.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private fun hardware() = AndroidSensorHardware.desde(contexto)

    @Test fun lasCapacidadesInformadasCoincidenConLasDelSistema() {
        val hw = hardware()
        for ((tipo, android) in listOf(TipoSensor.ACELEROMETRO to Sensor.TYPE_ACCELEROMETER, TipoSensor.PROXIMIDAD to Sensor.TYPE_PROXIMITY)) {
            val real = manager.getDefaultSensor(android)
            val info = hw.informacion(tipo)
            if (real == null) assertEquals(null, info)
            else { assertNotNull(info); assertEquals(real.maximumRange, info!!.rangoMaximo, 0f) }
        }
    }

    @Test fun elAcelerometroRealEntregaLecturasFinitasYSeLibera() {
        val hw = hardware()
        assumeTrue("El dispositivo no tiene acelerómetro", hw.informacion(TipoSensor.ACELEROMETRO) != null)
        val recibida = CountDownLatch(1)
        var valores: FloatArray? = null
        val escucha = EscuchaSensor { v, _, _ -> if (valores == null) { valores = v; recibida.countDown() } }
        assertTrue(hw.registrar(TipoSensor.ACELEROMETRO, escucha))
        assertEquals(1, hw.suscripcionesActivas())
        assertTrue("No llegó ninguna lectura en 5 s", recibida.await(5, TimeUnit.SECONDS))
        hw.liberar(escucha)
        assertEquals(0, hw.suscripcionesActivas())
        hw.liberar(escucha) // idempotente
        val lectura = valores!!
        assertTrue(lectura.size >= 3 && lectura.take(3).all { it.isFinite() })
    }

    @Test fun elFlujoRealDeAcelerometroYProximidadNoDejaSuscripcionesTrasCancelar() {
        val hw = hardware()
        assumeTrue("El dispositivo no tiene acelerómetro", hw.informacion(TipoSensor.ACELEROMETRO) != null)
        val fuente = FuenteSensores(hw)
        runBlocking {
            val job = launch(Dispatchers.Main) { fuente.acelerometro().collect { } }
            val job2 = launch(Dispatchers.Main) { fuente.proximidad().collect { } }
            delay(500)
            assertTrue(hw.suscripcionesActivas() >= 1)
            job.cancel(); job2.cancel(); job.join(); job2.join()
        }
        assertEquals(0, hw.suscripcionesActivas())
    }

    @Test fun registrarUnSensorAusenteDevuelveFalsoSinExcepcion() {
        val sinGestor = AndroidSensorHardware(null)
        assertFalse(sinGestor.registrar(TipoSensor.ACELEROMETRO, EscuchaSensor { _, _, _ -> }))
        assertEquals(null, sinGestor.informacion(TipoSensor.PROXIMIDAD))
        sinGestor.liberar(EscuchaSensor { _, _, _ -> })
    }
}
