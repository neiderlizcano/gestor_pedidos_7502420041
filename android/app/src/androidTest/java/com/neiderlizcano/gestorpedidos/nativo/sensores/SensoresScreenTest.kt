package com.neiderlizcano.gestorpedidos.nativo.sensores

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

// Hardware simulado seguro entre hilos: las lecturas llegan desde el hilo de la prueba.
private class HardwareDePrueba(
    var sensores: Map<TipoSensor, InfoSensor> = mapOf(
        TipoSensor.ACELEROMETRO to InfoSensor("Acelerómetro de prueba", 78.4f),
        TipoSensor.PROXIMIDAD to InfoSensor("Proximidad de prueba", 5f)
    )
) : SensorHardware {
    @Volatile var aceptaRegistro = true
    val registros = AtomicInteger()
    private val activas = java.util.concurrent.CopyOnWriteArrayList<Pair<TipoSensor, EscuchaSensor>>()
    val activasCount: Int get() = activas.size

    override fun informacion(tipo: TipoSensor) = sensores[tipo]
    override fun registrar(tipo: TipoSensor, escucha: EscuchaSensor): Boolean {
        if (!aceptaRegistro) return false
        registros.incrementAndGet(); activas += tipo to escucha; return true
    }
    override fun liberar(escucha: EscuchaSensor) { activas.removeAll { it.second === escucha } }
    fun emitir(tipo: TipoSensor, vararg valores: Float, instanteNs: Long = 0L) {
        activas.filter { it.first == tipo }.forEach { it.second.alRecibir(valores.copyOf(), 3, instanteNs) }
    }
}

class SensoresScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val contexto get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun texto(id: Int, vararg args: Any) = contexto.getString(id, *args)

    private fun mostrar(hw: SensorHardware) {
        compose.setContent { GestorPedidosTheme { SensoresRoute(hw) } }
        compose.waitForIdle()
    }

    private fun esperarTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    // Sondeo simple: sigue funcionando aunque la actividad ya no tenga jerarquía de Compose.
    private fun esperarSuscripciones(hw: HardwareDePrueba, n: Int) {
        val limite = System.currentTimeMillis() + 5_000
        while (hw.activasCount != n && System.currentTimeMillis() < limite) Thread.sleep(50)
        assertEquals(n, hw.activasCount)
    }

    @Test fun muestraEstadoDeEsperaAntesDeLaPrimeraLectura() {
        val hw = HardwareDePrueba(); mostrar(hw)
        compose.onNodeWithTag("accEstado").assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.sensores_acc_waiting)).assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.sensores_prox_waiting)).performScrollTo().assertIsDisplayed()
    }

    @Test fun muestraEjesXYZYDetectaUnaSacudida() {
        val hw = HardwareDePrueba(); mostrar(hw); esperarSuscripciones(hw, 2)
        hw.emitir(TipoSensor.ACELEROMETRO, 0.5f, 9.75f, -0.25f, instanteNs = 0L)
        esperarTag("accX")
        compose.onNodeWithTag("accX").assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.sensores_acc_axis, "Y", String.format(java.util.Locale.getDefault(), "%.2f", 9.75f))).assertIsDisplayed()
        compose.onNodeWithTag("accReposo").assertIsDisplayed()
        hw.emitir(TipoSensor.ACELEROMETRO, 0f, 25f, 0f, instanteNs = 1_000_000_000L)
        esperarTag("accMovimiento")
        compose.onNodeWithText(texto(R.string.sensores_acc_count, 1)).assertIsDisplayed()
    }

    @Test fun proximidadMuestraCercaYLejos() {
        val hw = HardwareDePrueba(); mostrar(hw); esperarSuscripciones(hw, 2)
        hw.emitir(TipoSensor.PROXIMIDAD, 0f); esperarTag("proxCerca")
        compose.onNodeWithText(texto(R.string.sensores_prox_near)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("proxBinario").assertExists()
        hw.emitir(TipoSensor.PROXIMIDAD, 5f); esperarTag("proxLejos")
        compose.onNodeWithText(texto(R.string.sensores_prox_far)).performScrollTo().assertIsDisplayed()
    }

    @Test fun sinSensoresMuestraMensajesClarosSinSuscribirse() {
        val hw = HardwareDePrueba(sensores = emptyMap()); mostrar(hw)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("accEstado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(texto(R.string.sensores_acc_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.sensores_prox_unavailable)).performScrollTo().assertIsDisplayed()
        assertEquals(0, hw.registros.get())
    }

    @Test fun unRegistroRechazadoMuestraErrorYElBotonReintenta() {
        val hw = HardwareDePrueba().apply { aceptaRegistro = false }; mostrar(hw)
        esperarTag("accEstadoReintentar")
        // Ambos paneles (acelerómetro y proximidad) muestran el error de registro.
        compose.onAllNodesWithText(texto(R.string.sensores_error_register)).assertCountEquals(2)
        hw.aceptaRegistro = true
        compose.onNodeWithTag("accEstadoReintentar").performScrollTo().performClick()
        esperarSuscripciones(hw, 1) // el acelerómetro se registra; la proximidad sigue en error hasta su reintento
        hw.emitir(TipoSensor.ACELEROMETRO, 0f, 9.8f, 0f); esperarTag("accX")
    }

    @Test fun elSensorSeLiberaEnSegundoPlanoYSeReactivaAlVolver() {
        val hw = HardwareDePrueba(); mostrar(hw); esperarSuscripciones(hw, 2)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // onStop: la pantalla deja de ser visible
        esperarSuscripciones(hw, 0)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        esperarSuscripciones(hw, 2)
        assertEquals(4, hw.registros.get())
    }

    @Test fun alDestruirLaActividadNoQuedanSuscripciones() {
        val hw = HardwareDePrueba(); mostrar(hw); esperarSuscripciones(hw, 2)
        compose.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        esperarSuscripciones(hw, 0)
    }
}
