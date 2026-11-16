package com.neiderlizcano.gestorpedidos.nativo.sensores

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensoresViewModelsTest {
    private fun TestScope.acelerometro(hw: FakeSensorHardware) = AcelerometroViewModel(FuenteSensores(hw), backgroundScope)
    private fun TestScope.proximidad(hw: FakeSensorHardware) = ProximidadViewModel(FuenteSensores(hw), backgroundScope)
    private fun TestScope.observar(vm: AcelerometroViewModel) = backgroundScope.launch { vm.uiState.collect { } }
    private fun TestScope.observar(vm: ProximidadViewModel) = backgroundScope.launch { vm.uiState.collect { } }

    @Test fun sinObservadoresElSensorNoSeRegistra() = runTest {
        val hw = FakeSensorHardware(); val vm = acelerometro(hw)
        runCurrent()
        assertEquals(0, hw.registros); assertEquals(AcelerometroUi.Esperando, vm.uiState.value)
    }

    @Test fun elSensorSeLiberaAlDejarDeObservarYSeReactivaAlVolver() = runTest {
        val hw = FakeSensorHardware(); val vm = acelerometro(hw)
        val primera = observar(vm); runCurrent()
        assertEquals(1, hw.suscripcionesActivas)
        primera.cancel(); runCurrent() // pantalla en segundo plano (onStop)
        assertEquals(0, hw.suscripcionesActivas); assertEquals(1, hw.liberaciones)
        val segunda = observar(vm); runCurrent() // vuelve a primer plano (onStart)
        assertEquals(1, hw.suscripcionesActivas); assertEquals(2, hw.registros)
        segunda.cancel(); runCurrent()
        assertEquals(0, hw.suscripcionesActivas)
    }

    @Test fun elAcelerometroMuestraEjesMagnitudYReposo() = runTest {
        val hw = FakeSensorHardware(); val vm = acelerometro(hw); observar(vm); runCurrent()
        assertEquals(AcelerometroUi.Esperando, vm.uiState.value)
        hw.emitir(TipoSensor.ACELEROMETRO, 0.1f, 9.8f, 0.2f, instanteNs = ms(0)); runCurrent()
        val activo = vm.uiState.value as AcelerometroUi.Activo
        assertEquals(0.1f, activo.x); assertEquals(9.8f, activo.y); assertEquals(0.2f, activo.z)
        assertEquals(9.8f, activo.magnitud, 0.01f)
        assertFalse(activo.movimiento); assertEquals(0, activo.detecciones)
    }

    @Test fun lasSacudidasActivanElAvisoLoMantienenUnTiempoYSeCuentan() = runTest {
        val hw = FakeSensorHardware(); val vm = acelerometro(hw); observar(vm); runCurrent()
        fun emitir(y: Float, t: Long): AcelerometroUi.Activo {
            hw.emitir(TipoSensor.ACELEROMETRO, 0f, y, 0f, instanteNs = ms(t)); runCurrent()
            return vm.uiState.value as AcelerometroUi.Activo
        }
        assertFalse(emitir(9.8f, 0).movimiento)
        val sacudida = emitir(25f, 1000)
        assertTrue(sacudida.movimiento); assertEquals(1, sacudida.detecciones)
        assertTrue(emitir(9.8f, 1500).movimiento) // el aviso se mantiene 1,5 s
        assertFalse(emitir(9.8f, 3000).movimiento)
        assertEquals(1, (vm.uiState.value as AcelerometroUi.Activo).detecciones)
        assertEquals(2, emitir(25f, 3100).detecciones)
    }

    @Test fun sinAcelerometroSeMuestraNoDisponible() = runTest {
        val hw = FakeSensorHardware(sensores = emptyMap()); val vm = acelerometro(hw); observar(vm); runCurrent()
        assertEquals(AcelerometroUi.NoDisponible, vm.uiState.value)
    }

    @Test fun datosInvalidosNoDerribanLaPantallaYSeRecupera() = runTest {
        val hw = FakeSensorHardware(); val vm = acelerometro(hw); observar(vm); runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, Float.NaN, 0f, 0f); runCurrent()
        assertEquals(AcelerometroUi.DatosNoDisponibles, vm.uiState.value)
        hw.emitir(TipoSensor.ACELEROMETRO, 0f, 9.8f, 0f); runCurrent()
        assertTrue(vm.uiState.value is AcelerometroUi.Activo)
    }

    @Test fun unRegistroRechazadoSeMuestraComoErrorYPermiteReintentar() = runTest {
        val hw = FakeSensorHardware().apply { aceptaRegistro = false }
        val vm = acelerometro(hw); observar(vm); runCurrent()
        assertEquals(AcelerometroUi.Error(MotivoErrorSensor.REGISTRO_RECHAZADO), vm.uiState.value)
        hw.aceptaRegistro = true
        vm.reintentar(); runCurrent()
        hw.emitir(TipoSensor.ACELEROMETRO, 0f, 9.8f, 0f); runCurrent()
        assertTrue(vm.uiState.value is AcelerometroUi.Activo)
        assertEquals(1, hw.suscripcionesActivas)
    }

    @Test fun reintentarNoDejaSuscripcionesDuplicadas() = runTest {
        val hw = FakeSensorHardware(); val vm = acelerometro(hw); observar(vm); runCurrent()
        vm.reintentar(); runCurrent(); vm.reintentar(); runCurrent()
        assertEquals(1, hw.suscripcionesActivas)
    }

    @Test fun proximidadMuestraCercaYLejos() = runTest {
        val hw = FakeSensorHardware(); val vm = proximidad(hw); observar(vm); runCurrent()
        assertEquals(ProximidadUi.Esperando, vm.uiState.value)
        hw.emitir(TipoSensor.PROXIMIDAD, 0f); runCurrent()
        assertEquals(ProximidadUi.Medida(cerca = true, distanciaCm = 0f, rangoMaximoCm = 5f, binario = true), vm.uiState.value)
        hw.emitir(TipoSensor.PROXIMIDAD, 5f); runCurrent()
        assertFalse((vm.uiState.value as ProximidadUi.Medida).cerca)
    }

    @Test fun proximidadSinSensorYConErrorYDatosInvalidos() = runTest {
        val sinSensor = proximidad(FakeSensorHardware(sensores = emptyMap())); observar(sinSensor); runCurrent()
        assertEquals(ProximidadUi.NoDisponible, sinSensor.uiState.value)

        val rechazado = proximidad(FakeSensorHardware().apply { aceptaRegistro = false }); observar(rechazado); runCurrent()
        assertEquals(ProximidadUi.Error(MotivoErrorSensor.REGISTRO_RECHAZADO), rechazado.uiState.value)

        val hw = FakeSensorHardware(); val vm = proximidad(hw); observar(vm); runCurrent()
        hw.emitir(TipoSensor.PROXIMIDAD, Float.NaN); runCurrent()
        assertEquals(ProximidadUi.DatosNoDisponibles, vm.uiState.value)
    }

    @Test fun proximidadSeLiberaAlDejarDeObservar() = runTest {
        val hw = FakeSensorHardware(); val vm = proximidad(hw)
        val job = observar(vm); runCurrent()
        assertEquals(1, hw.suscripcionesActivas)
        job.cancel(); runCurrent()
        assertEquals(0, hw.suscripcionesActivas)
    }

    @Test fun unDetectorQueFallaSeConvierteEnErrorInesperado() = runTest {
        val hw = FakeSensorHardware()
        val vm = AcelerometroViewModel(FuenteSensores(hw), backgroundScope) { error("fallo del detector") }
        observar(vm); runCurrent()
        assertEquals(AcelerometroUi.Error(MotivoErrorSensor.FALLO_INESPERADO), vm.uiState.value)
    }
}
