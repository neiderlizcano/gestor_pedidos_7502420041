package com.neiderlizcano.gestorpedidos.nativo.sensores

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FuenteSensoresTest {
    @Test fun alRecolectarSeRegistraYAlCancelarSeLibera() = runTest {
        val hw = FakeSensorHardware(); val fuente = FuenteSensores(hw)
        assertEquals(0, hw.registros)
        val job = launch { fuente.acelerometro().collect { } }
        runCurrent()
        assertEquals(1, hw.registros); assertEquals(1, hw.suscripcionesActivas)
        job.cancel(); runCurrent()
        assertEquals(1, hw.liberaciones); assertEquals(0, hw.suscripcionesActivas)
    }

    @Test fun cadaRecoleccionTieneSuPropiaSuscripcionYTodasSeLiberan() = runTest {
        val hw = FakeSensorHardware(); val fuente = FuenteSensores(hw)
        val a = launch { fuente.acelerometro().collect { } }
        val b = launch { fuente.proximidad().collect { } }
        runCurrent()
        assertEquals(2, hw.suscripcionesActivas)
        a.cancel(); b.cancel(); runCurrent()
        assertEquals(0, hw.suscripcionesActivas); assertEquals(2, hw.liberaciones)
    }

    @Test fun sinSensorEmiteNoDisponibleYNuncaRegistra() = runTest {
        val hw = FakeSensorHardware(sensores = emptyMap()); val fuente = FuenteSensores(hw)
        val eventos = mutableListOf<EventoSensor<*>>()
        val job = launch { fuente.acelerometro().collect { eventos += it } }
        val job2 = launch { fuente.proximidad().collect { eventos += it } }
        runCurrent()
        assertEquals(listOf(EventoSensor.NoDisponible, EventoSensor.NoDisponible), eventos)
        assertEquals(0, hw.registros)
        job.cancel(); job2.cancel()
    }

    @Test fun siElSistemaRechazaElRegistroSeEmiteErrorSinFugas() = runTest {
        val hw = FakeSensorHardware().apply { aceptaRegistro = false }
        val eventos = mutableListOf<EventoSensor<*>>()
        val job = launch { FuenteSensores(hw).acelerometro().collect { eventos += it } }
        runCurrent()
        assertEquals(listOf<EventoSensor<*>>(EventoSensor.Error(MotivoErrorSensor.REGISTRO_RECHAZADO)), eventos)
        assertEquals(0, hw.suscripcionesActivas)
        job.cancel()
    }

    @Test fun unaExcepcionAlRegistrarSeTratacomoRegistroRechazado() = runTest {
        val hw = FakeSensorHardware().apply { lanzaAlRegistrar = true }
        val eventos = mutableListOf<EventoSensor<*>>()
        val job = launch { FuenteSensores(hw).proximidad().collect { eventos += it } }
        runCurrent()
        assertEquals(listOf<EventoSensor<*>>(EventoSensor.Error(MotivoErrorSensor.REGISTRO_RECHAZADO)), eventos)
        job.cancel()
    }

    @Test fun lasLecturasDelAcelerometroSeConviertenYSeMarcaLaPrecisionBaja() = runTest {
        val hw = FakeSensorHardware(); val recibidos = mutableListOf<EventoSensor<LecturaAcelerometro>>()
        val job = launch { FuenteSensores(hw).acelerometro().collect { recibidos += it } }
        runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, 1f, 2f, 9.8f, precision = 3, instanteNs = 5L); runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, 0f, 0f, 9.8f, precision = 1, instanteNs = 6L); runCurrent()
        val lecturas = recibidos.filterIsInstance<EventoSensor.Lectura<LecturaAcelerometro>>().map { it.valor }
        assertEquals(LecturaAcelerometro(1f, 2f, 9.8f, 5L, false), lecturas[0])
        assertTrue(lecturas[1].precisionBaja)
        job.cancel()
    }

    @Test fun valoresIncompletosONoFinitosSonDatosNoDisponiblesYElFlujoSeRecupera() = runTest {
        val hw = FakeSensorHardware(); val recibidos = mutableListOf<EventoSensor<LecturaAcelerometro>>()
        val job = launch { FuenteSensores(hw).acelerometro().collect { recibidos += it } }
        runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, 1f, 2f); runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, Float.NaN, 0f, 0f); runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, 0f, Float.POSITIVE_INFINITY, 0f); runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, 0f, 9.8f, 0f); runCurrent()
        assertEquals(3, recibidos.count { it == EventoSensor.DatosNoDisponibles })
        assertTrue(recibidos.last() is EventoSensor.Lectura)
        job.cancel()
    }

    @Test fun proximidadBinariaDistingueCercaYLejos() = runTest {
        val hw = FakeSensorHardware(); val recibidos = mutableListOf<EventoSensor<LecturaProximidad>>()
        val job = launch { FuenteSensores(hw).proximidad().collect { recibidos += it } }
        runCurrent()
        hw.emitir(TipoSensor.PROXIMIDAD, 0f); runCurrent()
        hw.emitir(TipoSensor.PROXIMIDAD, 5f); runCurrent()
        val lecturas = recibidos.filterIsInstance<EventoSensor.Lectura<LecturaProximidad>>().map { it.valor }
        assertTrue(lecturas[0].cerca); assertFalse(lecturas[1].cerca)
        assertTrue(lecturas.all { it.esBinario })
        job.cancel()
    }

    @Test fun proximidadContinuaUsaElUmbralDeCincoCentimetros() {
        val rango = 10f
        assertTrue(LecturaProximidad(4.9f, rango).cerca); assertFalse(LecturaProximidad(5f, rango).cerca)
        assertFalse(LecturaProximidad(4.9f, rango).esBinario)
        // Con un rango menor que 5 cm la referencia es el propio máximo del sensor.
        assertTrue(LecturaProximidad(0f, 1f).cerca); assertFalse(LecturaProximidad(1f, 1f).cerca)
    }

    @Test fun proximidadConValoresInvalidosEsDatosNoDisponibles() = runTest {
        val hw = FakeSensorHardware(); val recibidos = mutableListOf<EventoSensor<LecturaProximidad>>()
        val job = launch { FuenteSensores(hw).proximidad().collect { recibidos += it } }
        runCurrent()
        hw.emitir(TipoSensor.PROXIMIDAD, -1f); runCurrent()
        hw.emitir(TipoSensor.PROXIMIDAD, Float.NaN); runCurrent()
        assertEquals(listOf(EventoSensor.DatosNoDisponibles, EventoSensor.DatosNoDisponibles), recibidos)
        job.cancel()
    }

    @Test fun proximidadConRangoMaximoInvalidoNoEmiteLecturas() = runTest {
        val hw = FakeSensorHardware(sensores = mapOf(TipoSensor.PROXIMIDAD to InfoSensor("Roto", 0f)))
        val recibidos = mutableListOf<EventoSensor<LecturaProximidad>>()
        val job = launch { FuenteSensores(hw).proximidad().collect { recibidos += it } }
        runCurrent()
        hw.emitir(TipoSensor.PROXIMIDAD, 0f); runCurrent()
        assertEquals(listOf(EventoSensor.DatosNoDisponibles), recibidos)
        job.cancel()
    }
}
