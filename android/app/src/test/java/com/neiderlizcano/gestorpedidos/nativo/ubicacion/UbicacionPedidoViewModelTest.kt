package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import android.location.LocationManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class ProveedorFalso(var resultado: ResultadoPosicion = ResultadoPosicion.Exito(Posicion(10.0, -75.0, 12f))) : ProveedorPosicion {
    var llamadas = 0
    var ultimaPrecisa: Boolean? = null
    var compuerta: CompletableDeferred<Unit>? = null
    var lanza: RuntimeException? = null
    override suspend fun obtenerActual(precisa: Boolean): ResultadoPosicion {
        llamadas++; ultimaPrecisa = precisa
        lanza?.let { throw it }
        compuerta?.await()
        return resultado
    }
}

private class GeocodificadorFalso(
    var resultado: ResultadoGeocodificacion = ResultadoGeocodificacion.Exito(
        DireccionCruda("Colombia", "Bolívar", "Cartagena", "Cartagena de Indias", "Carrera 5", "12-34")
    )
) : GeocodificadorInverso {
    var llamadas = 0
    var compuerta: CompletableDeferred<Unit>? = null
    override suspend fun resolver(posicion: Posicion): ResultadoGeocodificacion {
        llamadas++; compuerta?.await(); return resultado
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionPedidoViewModelTest {
    private fun TestScope.vm(p: ProveedorFalso, g: GeocodificadorFalso) = UbicacionPedidoViewModel(p, g, backgroundScope)

    @Test fun cancelarYReintentarSinEsperarNoDejaQueElIntentoAnteriorBorreElNuevoEstado() = runTest {
        val g = GeocodificadorFalso().apply { compuerta = CompletableDeferred() }
        val vm = vm(ProveedorFalso(), g)
        vm.iniciar(false); runCurrent()
        assertEquals(EstadoUbicacion.ResolviendoDireccion, vm.uiState.value)
        vm.cancelar(); vm.iniciar(false); runCurrent()
        assertTrue(vm.ocupado)
        assertEquals(EstadoUbicacion.ResolviendoDireccion, vm.uiState.value)
        g.compuerta!!.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value is EstadoUbicacion.Completado)
        vm.sugerencias.first()
        assertNull(withTimeoutOrNull(1_000) { vm.sugerencias.first() })
    }

    @Test fun crearElViewModelNoPideNadaNiUsaServicios() = runTest {
        val p = ProveedorFalso(); val g = GeocodificadorFalso(); val vm = vm(p, g)
        runCurrent()
        assertEquals(EstadoUbicacion.Inactivo, vm.uiState.value)
        assertEquals(0, p.llamadas); assertEquals(0, g.llamadas)
    }

    @Test fun alPulsarConPermisoPrecisoEntregaUnaSugerenciaCompleta() = runTest {
        val p = ProveedorFalso(); val g = GeocodificadorFalso(); val vm = vm(p, g)
        vm.iniciar(aproximado = false); runCurrent()
        val sugerida = vm.sugerencias.first()
        assertEquals("Carrera 5 #12-34", sugerida.nomenclatura); assertEquals("Cartagena de Indias", sugerida.ciudad)
        assertEquals(true, p.ultimaPrecisa)
        assertEquals(EstadoUbicacion.Completado(emptySet(), emptySet()), vm.uiState.value)
    }

    @Test fun conPermisoAproximadoSePideUbicacionNoPrecisaYSeAvisa() = runTest {
        val p = ProveedorFalso(); val vm = vm(p, GeocodificadorFalso())
        vm.iniciar(aproximado = true); runCurrent()
        assertEquals(false, p.ultimaPrecisa)
        assertTrue(AvisoDireccion.PRECISION_APROXIMADA in vm.sugerencias.first().avisos)
    }

    @Test fun laSugerenciaEsUnEventoDeUnSoloUso() = runTest {
        val vm = vm(ProveedorFalso(), GeocodificadorFalso())
        vm.iniciar(false); runCurrent()
        vm.sugerencias.first()
        assertNull(withTimeoutOrNull(1_000) { vm.sugerencias.first() })
    }

    @Test fun datosParcialesSeEntreganConLosFaltantesIndicados() = runTest {
        val g = GeocodificadorFalso(ResultadoGeocodificacion.Exito(DireccionCruda(pais = "Colombia", departamento = "Bolívar")))
        val vm = vm(ProveedorFalso(), g)
        vm.iniciar(false); runCurrent()
        val s = vm.sugerencias.first()
        assertNull(s.ciudad); assertNull(s.nomenclatura)
        assertEquals(setOf(CampoDireccion.CIUDAD, CampoDireccion.NOMENCLATURA), (vm.uiState.value as EstadoUbicacion.Completado).faltantes)
    }

    @Test fun unaDireccionSinNingunDatoUtilEsUnErrorYNoEntregaNada() = runTest {
        val g = GeocodificadorFalso(ResultadoGeocodificacion.Exito(DireccionCruda()))
        val vm = vm(ProveedorFalso(), g)
        vm.iniciar(false); runCurrent()
        assertEquals(EstadoUbicacion.Error(ErrorUbicacion.SIN_RESULTADOS), vm.uiState.value)
        assertNull(withTimeoutOrNull(1_000) { vm.sugerencias.first() })
    }

    @Test fun cadaFalloDeUbicacionTieneSuEstado() = runTest {
        val casos = mapOf(
            ResultadoPosicion.UbicacionDesactivada to EstadoUbicacion.UbicacionDesactivada,
            ResultadoPosicion.PermisoDenegado to EstadoUbicacion.PermisoDenegado(false),
            ResultadoPosicion.SinProveedor to EstadoUbicacion.Error(ErrorUbicacion.SIN_PROVEEDOR),
            ResultadoPosicion.TiempoAgotado to EstadoUbicacion.Error(ErrorUbicacion.TIEMPO_AGOTADO),
            ResultadoPosicion.Fallo to EstadoUbicacion.Error(ErrorUbicacion.FALLO)
        )
        for ((resultado, esperado) in casos) {
            val g = GeocodificadorFalso(); val vm = vm(ProveedorFalso(resultado), g)
            vm.iniciar(false); runCurrent()
            assertEquals(resultado.toString(), esperado, vm.uiState.value)
            assertEquals("sin posición no se geocodifica", 0, g.llamadas)
        }
    }

    @Test fun cadaFalloDeGeocodificacionTieneSuEstado() = runTest {
        val casos = mapOf(
            ResultadoGeocodificacion.SinResultados to ErrorUbicacion.SIN_RESULTADOS,
            ResultadoGeocodificacion.TiempoAgotado to ErrorUbicacion.TIEMPO_AGOTADO,
            ResultadoGeocodificacion.SinConexion to ErrorUbicacion.SIN_CONEXION,
            ResultadoGeocodificacion.ServicioNoDisponible to ErrorUbicacion.SERVICIO_NO_DISPONIBLE,
            ResultadoGeocodificacion.Fallo to ErrorUbicacion.FALLO
        )
        for ((resultado, error) in casos) {
            val vm = vm(ProveedorFalso(), GeocodificadorFalso(resultado))
            vm.iniciar(false); runCurrent()
            assertEquals(resultado.toString(), EstadoUbicacion.Error(error), vm.uiState.value)
        }
    }

    @Test fun cancelarMientrasSeObtieneLaPosicionNoEntregaNadaNiGeocodifica() = runTest {
        val p = ProveedorFalso().apply { compuerta = CompletableDeferred() }; val g = GeocodificadorFalso(); val vm = vm(p, g)
        vm.iniciar(false); runCurrent()
        assertEquals(EstadoUbicacion.ObteniendoPosicion, vm.uiState.value)
        vm.cancelar(); runCurrent()
        assertEquals(EstadoUbicacion.Inactivo, vm.uiState.value)
        p.compuerta!!.complete(Unit); runCurrent()
        assertEquals(0, g.llamadas); assertFalse(vm.ocupado)
        assertNull(withTimeoutOrNull(1_000) { vm.sugerencias.first() })
    }

    @Test fun cancelarMientrasSeBuscaLaDireccionNoEntregaNada() = runTest {
        val g = GeocodificadorFalso().apply { compuerta = CompletableDeferred() }; val vm = vm(ProveedorFalso(), g)
        vm.iniciar(false); runCurrent()
        assertEquals(EstadoUbicacion.ResolviendoDireccion, vm.uiState.value)
        vm.cancelar(); runCurrent(); g.compuerta!!.complete(Unit); runCurrent()
        assertEquals(EstadoUbicacion.Inactivo, vm.uiState.value)
        assertNull(withTimeoutOrNull(1_000) { vm.sugerencias.first() })
    }

    @Test fun pulsacionesRepetidasMientrasSeTrabajaSeIgnoran() = runTest {
        val p = ProveedorFalso().apply { compuerta = CompletableDeferred() }; val vm = vm(p, GeocodificadorFalso())
        vm.iniciar(false); vm.iniciar(false); vm.iniciar(true); runCurrent()
        assertEquals(1, p.llamadas); assertTrue(vm.ocupado)
        p.compuerta!!.complete(Unit); runCurrent()
        assertFalse(vm.ocupado)
    }

    @Test fun tras_unErrorSePuedeReintentarYTerminaBien() = runTest {
        val p = ProveedorFalso(ResultadoPosicion.TiempoAgotado); val vm = vm(p, GeocodificadorFalso())
        vm.iniciar(false); runCurrent()
        assertEquals(EstadoUbicacion.Error(ErrorUbicacion.TIEMPO_AGOTADO), vm.uiState.value)
        p.resultado = ResultadoPosicion.Exito(Posicion(10.0, -75.0))
        vm.iniciar(false); runCurrent()
        assertTrue(vm.uiState.value is EstadoUbicacion.Completado)
        assertEquals("Colombia", vm.sugerencias.first().pais)
    }

    @Test fun elPermisoDenegadoSeDistingueDelPermanente() = runTest {
        val vm = vm(ProveedorFalso(), GeocodificadorFalso())
        vm.permisoSolicitado(); assertEquals(EstadoUbicacion.SolicitandoPermiso, vm.uiState.value)
        vm.permisoDenegado(permanente = false); assertEquals(EstadoUbicacion.PermisoDenegado(false), vm.uiState.value)
        vm.permisoDenegado(permanente = true); assertEquals(EstadoUbicacion.PermisoDenegado(true), vm.uiState.value)
        vm.descartarMensaje(); assertEquals(EstadoUbicacion.Inactivo, vm.uiState.value)
    }

    @Test fun unaExcepcionInesperadaDelProveedorNoDerribaLaPantalla() = runTest {
        val p = ProveedorFalso().apply { lanza = IllegalStateException("fallo simulado") }; val vm = vm(p, GeocodificadorFalso())
        vm.iniciar(false); runCurrent()
        assertEquals(EstadoUbicacion.Error(ErrorUbicacion.FALLO), vm.uiState.value)
    }

    @Test fun elProveedorSeEligeSegunPermisoYVersion() {
        val todos = setOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        assertEquals("fused", elegirProveedor(todos, precisa = true, sdk = 34))
        assertEquals(LocationManager.GPS_PROVIDER, elegirProveedor(todos - "fused", precisa = true, sdk = 34))
        assertEquals(LocationManager.GPS_PROVIDER, elegirProveedor(todos, precisa = true, sdk = 30))
        assertEquals(LocationManager.NETWORK_PROVIDER, elegirProveedor(todos, precisa = false, sdk = 30))
        assertEquals("fused", elegirProveedor(todos, precisa = false, sdk = 31))
        assertEquals(LocationManager.NETWORK_PROVIDER, elegirProveedor(setOf(LocationManager.NETWORK_PROVIDER), precisa = true, sdk = 33))
        assertNull(elegirProveedor(setOf(LocationManager.GPS_PROVIDER), precisa = false, sdk = 30))
        assertNull(elegirProveedor(emptySet(), precisa = true, sdk = 34))
    }

    @Test fun siUnProveedorNoEntregaPosicionSePruebaElSiguienteEnOrden() {
        val todos = setOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        assertEquals(listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER), proveedoresEnOrden(todos, true, 34))
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), proveedoresEnOrden(todos, false, 30))
        assertEquals(emptyList<String>(), proveedoresEnOrden(setOf(LocationManager.GPS_PROVIDER), false, 30))
    }
}
