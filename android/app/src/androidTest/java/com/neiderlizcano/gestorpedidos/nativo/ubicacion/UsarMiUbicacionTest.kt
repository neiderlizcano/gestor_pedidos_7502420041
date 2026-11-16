package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Dobles de las pruebas instrumentadas: la interfaz real (Compose, ViewModel, permisos vía registro de resultados)
// con proveedor y geocodificador simulados, para cubrir cada ruta sin depender del GPS ni de Internet.
private class ProveedorUi(var resultado: ResultadoPosicion = ResultadoPosicion.Exito(Posicion(10.0, -75.0))) : ProveedorPosicion {
    @Volatile var llamadas = 0
    @Volatile var precisa: Boolean? = null
    @Volatile var compuerta: CompletableDeferred<Unit>? = null
    override suspend fun obtenerActual(precisa: Boolean): ResultadoPosicion {
        llamadas++; this.precisa = precisa; compuerta?.await(); return resultado
    }
}

private class GeocodificadorUi(
    var resultado: ResultadoGeocodificacion = ResultadoGeocodificacion.Exito(
        DireccionCruda("Colombia", "Bolívar", "Cartagena", "Cartagena de Indias", "Carrera 5", "12-34")
    )
) : GeocodificadorInverso {
    @Volatile var llamadas = 0
    override suspend fun resolver(posicion: Posicion): ResultadoGeocodificacion { llamadas++; return resultado }
}

private class PermisosUi(var fino: Boolean = false, var grueso: Boolean = false, var permanente: Boolean = false) : PermisosUbicacion {
    override fun preciso() = fino
    override fun aproximado() = grueso
    override fun denegacionPermanente() = permanente
}

/** Registro que responde al instante con lo que decida la prueba, como si el usuario tocara el diálogo. */
private class RegistroUi(var respuesta: Map<String, Boolean>) : ActivityResultRegistry() {
    @Volatile var lanzamientos = 0
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        lanzamientos++
        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, respuesta as O)
    }
}

class UsarMiUbicacionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val contexto get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun texto(id: Int, vararg args: Any) = contexto.getString(id, *args)

    private class Escenario(
        val proveedor: ProveedorUi = ProveedorUi(),
        val geocodificador: GeocodificadorUi = GeocodificadorUi(),
        val permisos: PermisosUi = PermisosUi(),
        val registro: RegistroUi = RegistroUi(mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to true, Manifest.permission.ACCESS_COARSE_LOCATION to true
        ))
    ) {
        val recibidas = mutableListOf<DireccionSugerida>()
        val vm = UbicacionPedidoViewModel(proveedor, geocodificador)
    }

    private fun mostrar(e: Escenario, conFormulario: Boolean = false) {
        // Se usa la actividad directamente: `compose.setContent` solo puede llamarse una vez por prueba y la
        // prueba de recreación necesita volver a componer tras `recreate()`.
        compose.activityRule.scenario.onActivity { actividad ->
            actividad.setContent {
                val dueno = object : ActivityResultRegistryOwner { override val activityResultRegistry = e.registro }
                GestorPedidosTheme {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides dueno) {
                        if (conFormulario) FormularioDePrueba(e) else UsarMiUbicacion(e.vm, e.permisos, onDireccion = { e.recibidas += it })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun esperar(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun esperarTexto(valor: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(valor).fetchSemanticsNodes().isNotEmpty() }

    @androidx.compose.runtime.Composable
    private fun FormularioDePrueba(e: Escenario) {
        var pais by remember { mutableStateOf("") }
        var departamento by remember { mutableStateOf("") }
        var ciudad by remember { mutableStateOf("Mi ciudad escrita") }
        var nomenclatura by remember { mutableStateOf("") }
        Column {
            UsarMiUbicacion(e.vm, e.permisos, onDireccion = {
                it.pais?.let { v -> pais = v }; it.departamento?.let { v -> departamento = v }
                it.ciudad?.let { v -> ciudad = v }; it.nomenclatura?.let { v -> nomenclatura = v }
            })
            OutlinedTextField(pais, { pais = it }, modifier = Modifier.fillMaxWidth().testTag("fPais"))
            OutlinedTextField(departamento, { departamento = it }, modifier = Modifier.fillMaxWidth().testTag("fDepartamento"))
            OutlinedTextField(ciudad, { ciudad = it }, modifier = Modifier.fillMaxWidth().testTag("fCiudad"))
            OutlinedTextField(nomenclatura, { nomenclatura = it }, modifier = Modifier.fillMaxWidth().testTag("fNomenclatura"))
            Text("form")
        }
    }

    @Test fun permisoRevocadoDuranteLaConsultaNoSeConfundeConProveedorAusente() {
        val e = Escenario(proveedor = ProveedorUi(ResultadoPosicion.PermisoDenegado), permisos = PermisosUi(fino = true))
        mostrar(e); compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_permiso_denegado))
        assertTrue(e.recibidas.isEmpty()); assertEquals(0, e.geocodificador.llamadas)
    }

    @Test fun timeoutDeGeocodificacionMuestraReintentoSinModificarLosCampos() {
        val e = Escenario(geocodificador = GeocodificadorUi(ResultadoGeocodificacion.TiempoAgotado), permisos = PermisosUi(fino = true))
        mostrar(e, conFormulario = true); compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_error_tiempo))
        compose.onNodeWithTag("fCiudad").assertTextContains("Mi ciudad escrita")
        assertTrue(e.recibidas.isEmpty())
        e.geocodificador.resultado = ResultadoGeocodificacion.Exito(DireccionCruda(pais = "Colombia"))
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperar("ubicacionFaltantes"); compose.onNodeWithTag("fPais").assertTextContains("Colombia")
    }

    @Test fun falloGenericoNoSePresentaComoFaltaDeInternet() {
        val e = Escenario(geocodificador = GeocodificadorUi(ResultadoGeocodificacion.Fallo), permisos = PermisosUi(fino = true))
        mostrar(e); compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_error_fallo))
        assertEquals(0, compose.onAllNodesWithText(texto(R.string.ubicacion_error_conexion)).fetchSemanticsNodes().size)
    }

    @Test fun alMostrarseNoPideNiUsaNadaHastaQueElUsuarioPulsa() {
        val e = Escenario(); mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.ubicacion_hint)).assertIsDisplayed()
        assertEquals(0, e.registro.lanzamientos); assertEquals(0, e.proveedor.llamadas); assertEquals(0, e.geocodificador.llamadas)
    }

    @Test fun sinPermisoSolicitaloAlPulsarYAlConcederloCompletaLaDireccion() {
        val e = Escenario(); mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        compose.waitUntil(5_000) { e.recibidas.isNotEmpty() }
        assertEquals(1, e.registro.lanzamientos)
        val s = e.recibidas.single()
        assertEquals("Colombia", s.pais); assertEquals("Bolívar", s.departamento)
        assertEquals("Cartagena de Indias", s.ciudad); assertEquals("Carrera 5 #12-34", s.nomenclatura)
        esperarTexto(texto(R.string.ubicacion_completado))
    }

    @Test fun conPermisoYaConcedidoNoVuelveAPedirloYConAproximadoAvisa() {
        val e = Escenario(permisos = PermisosUi(grueso = true)); mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        compose.waitUntil(5_000) { e.recibidas.isNotEmpty() }
        assertEquals(0, e.registro.lanzamientos); assertEquals(false, e.proveedor.precisa)
        esperar("ubicacionAviso_PRECISION_APROXIMADA")
    }

    @Test fun permisoDenegadoMuestraMensajeYPermiteEscribirManualmente() {
        val e = Escenario(registro = RegistroUi(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false, Manifest.permission.ACCESS_COARSE_LOCATION to false)))
        mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_permiso_denegado))
        assertEquals(0, e.proveedor.llamadas)
        assertEquals(0, compose.onAllNodesWithTag("ubicacionAjustesApp").fetchSemanticsNodes().size)
        assertTrue(e.recibidas.isEmpty())
    }

    @Test fun permisoBloqueadoOfreceAbrirLosAjustes() {
        val e = Escenario(
            permisos = PermisosUi(permanente = true),
            registro = RegistroUi(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false, Manifest.permission.ACCESS_COARSE_LOCATION to false))
        )
        mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_permiso_bloqueado))
        compose.onNodeWithTag("ubicacionAjustesApp").assertIsDisplayed()
    }

    @Test fun conUbicacionDesactivadaSeIndicaYSeOfreceElAjuste() {
        val e = Escenario(proveedor = ProveedorUi(ResultadoPosicion.UbicacionDesactivada), permisos = PermisosUi(fino = true)); mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_desactivada))
        compose.onNodeWithTag("ubicacionAjustesUbicacion").assertIsDisplayed()
        assertEquals(0, e.geocodificador.llamadas)
    }

    @Test fun sinInternetSeExplicaSinRellenarNada() {
        val e = Escenario(geocodificador = GeocodificadorUi(ResultadoGeocodificacion.SinConexion), permisos = PermisosUi(fino = true)); mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_error_conexion))
        assertTrue(e.recibidas.isEmpty())
    }

    @Test fun elResultadoIncompletoSeAplicaSoloEnLoDisponibleYSeInformaLoQueFalta() {
        val parcial = GeocodificadorUi(ResultadoGeocodificacion.Exito(DireccionCruda(pais = "Colombia", departamento = "Bolívar", calle = "Carrera 5")))
        val e = Escenario(geocodificador = parcial, permisos = PermisosUi(fino = true)); mostrar(e, conFormulario = true)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperar("ubicacionFaltantes")
        compose.onNodeWithTag("fPais").assertTextContains("Colombia")
        compose.onNodeWithTag("fDepartamento").assertTextContains("Bolívar")
        compose.onNodeWithTag("fNomenclatura").assertTextContains("Carrera 5")
        // La ciudad no vino en el resultado: se conserva lo que el usuario había escrito.
        compose.onNodeWithTag("fCiudad").assertTextContains("Mi ciudad escrita")
        compose.onNodeWithTag("ubicacionAviso_SIN_NUMERO").assertIsDisplayed()
        compose.onNodeWithTag("ubicacionFaltantes").assertIsDisplayed()
    }

    @Test fun losCuatroCamposSiguenEditablesDespuesDeUsarLaUbicacion() {
        val e = Escenario(permisos = PermisosUi(fino = true)); mostrar(e, conFormulario = true)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperarTexto(texto(R.string.ubicacion_completado))
        for ((tag, nuevo) in listOf("fPais" to "Otro país", "fDepartamento" to "Otro depto", "fCiudad" to "Otra ciudad", "fNomenclatura" to "Calle 1 #2-3")) {
            compose.onNodeWithTag(tag).performTextClearance()
            compose.onNodeWithTag(tag).performTextInput(nuevo)
            compose.onNodeWithTag(tag).assertTextContains(nuevo)
        }
    }

    @Test fun cancelarDetieneLaBusquedaSinTocarElFormulario() {
        val e = Escenario(proveedor = ProveedorUi().apply { compuerta = CompletableDeferred() }, permisos = PermisosUi(fino = true))
        mostrar(e, conFormulario = true)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        esperar("ubicacionCancelar")
        compose.onNodeWithTag("ubicacionUsar").assertIsNotEnabled()
        compose.onNodeWithTag("ubicacionCancelar").performClick()
        esperarTexto(texto(R.string.ubicacion_hint))
        e.proveedor.compuerta!!.complete(Unit)
        compose.waitForIdle()
        assertTrue(e.recibidas.isEmpty()); assertEquals(0, e.geocodificador.llamadas)
        compose.onNodeWithTag("fCiudad").assertTextContains("Mi ciudad escrita")
    }

    @Test fun laSugerenciaNoSeVuelveAAplicarAlRecrearLaActividad() {
        val e = Escenario(permisos = PermisosUi(fino = true)); mostrar(e)
        compose.onNodeWithTag("ubicacionUsar").performClick()
        compose.waitUntil(5_000) { e.recibidas.isNotEmpty() }
        val antes = e.recibidas.size
        compose.activityRule.scenario.recreate()
        mostrar(e)
        compose.waitForIdle()
        assertEquals(antes, e.recibidas.size)
        assertNull(e.recibidas.getOrNull(antes))
    }
}
