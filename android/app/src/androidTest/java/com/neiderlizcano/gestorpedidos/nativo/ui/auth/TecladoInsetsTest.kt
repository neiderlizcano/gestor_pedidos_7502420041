package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

// Regresión del teclado: con el teclado visible el encabezado de la sesión NO debe crecer con su altura y el campo
// que se está llenando debe seguir a la vista por encima del teclado. Necesita un teclado en pantalla: en el emulador
// hay que activar «show_ime_with_hard_keyboard»; si no aparece, la prueba se omite (Assume) en vez de pasar en falso.
class TecladoInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun tecladoVisible(): Boolean {
        val vista = compose.activity.window.decorView
        return ViewCompat.getRootWindowInsets(vista)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun alturaTeclado(): Int =
        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

    @Test fun conElTecladoAbiertoElEncabezadoNoCreceYElCampoSigueVisible() {
        val runtime = PedidosTestRuntime()
        compose.activityRule.scenario.onActivity { it.setContent { AuthRoute(runtime) } }
        compose.waitForIdle(); compose.abrirPedidos()
        compose.onNodeWithTag("pedidoNuevo").performClick()
        compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag("pedidoField_CLIENTE"))
        compose.onNodeWithTag("pedidoField_CLIENTE").performClick()
        val limite = System.currentTimeMillis() + 6_000
        while (!tecladoVisible() && System.currentTimeMillis() < limite) { compose.waitForIdle(); Thread.sleep(100) }
        assumeTrue("No apareció el teclado en pantalla en este entorno", tecladoVisible())
        compose.waitForIdle()

        // Con el teclado abierto el área del formulario debe conservar altura útil. Antes, el encabezado de la sesión
        // sumaba la altura del teclado a su relleno y el formulario quedaba casi sin espacio en pantallas de ~1600 px.
        val densidad = compose.activity.resources.displayMetrics.density
        val area = compose.onNodeWithTag("pedidosScreen").fetchSemanticsNode().boundsInWindow
        assertTrue("El área del formulario quedó de ${area.height}px con el teclado abierto", area.height >= 180 * densidad)

        compose.onNodeWithTag("pedidoField_CLIENTE").assertIsDisplayed()
        val campo = compose.onNodeWithTag("pedidoField_CLIENTE").fetchSemanticsNode().boundsInWindow
        val altoPantalla = compose.activity.resources.displayMetrics.heightPixels
        val inicioTeclado = altoPantalla - alturaTeclado()
        assertTrue("El campo queda tapado por el teclado: bottom=${campo.bottom}px, teclado desde $inicioTeclado px", campo.bottom <= inicioTeclado + 1)
    }

    @Test fun laEtiquetaYElErrorDelCampoEnfocadoSeVenSobreElTeclado() {
        val runtime = PedidosTestRuntime()
        compose.activityRule.scenario.onActivity { it.setContent { AuthRoute(runtime) } }
        compose.waitForIdle(); compose.abrirPedidos()
        compose.onNodeWithTag("pedidoNuevo").performClick()
        compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag("pedidoGuardar"))
        compose.onNodeWithTag("pedidoGuardar").performClick() // formulario vacío: todos los campos muestran su error
        compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag("pedidoField_PROPINA"))
        compose.onNodeWithTag("pedidoField_PROPINA").performClick()
        val limite = System.currentTimeMillis() + 6_000
        while (!tecladoVisible() && System.currentTimeMillis() < limite) { compose.waitForIdle(); Thread.sleep(100) }
        assumeTrue("No apareció el teclado en pantalla en este entorno", tecladoVisible())
        Thread.sleep(900) // deja terminar la animación del teclado y el desplazamiento automático
        compose.waitForIdle()
        val altoPantalla = compose.activity.resources.displayMetrics.heightPixels
        val inicioTeclado = altoPantalla - alturaTeclado()
        val campo = compose.onNodeWithTag("pedidoField_PROPINA").fetchSemanticsNode().boundsInWindow
        val error = compose.onNodeWithTag("pedidoError_PROPINA", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        assertTrue("El campo queda tapado: bottom=${campo.bottom}, teclado desde $inicioTeclado", campo.bottom <= inicioTeclado + 1)
        assertTrue("El error queda tapado por el teclado: bottom=${error.bottom}, teclado desde $inicioTeclado", error.bottom <= inicioTeclado + 1)
    }
}
