package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.PedidoDraft
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.PedidoField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// Estado como lista cerrada y carga inicial tras entrar a la sesión.
class PedidosEstadoYCargaTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun install(runtime: PedidosTestRuntime) {
        compose.activityRule.scenario.onActivity { it.setContent { AuthRoute(runtime) } }
        compose.waitForIdle()
    }
    private fun show(tag: String) { compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag(tag)) }
    private fun click(tag: String) { show(tag); compose.onNodeWithTag(tag).performClick() }
    private fun menuItem(texto: String) = compose.onNode(hasText(texto) and hasAnyAncestor(hasTestTag("pedidoEstadoMenu")), useUnmergedTree = true)
    private fun enter(field: PedidoField, value: String) {
        val tag = "pedidoField_" + field.name
        show(tag)
        when (field) {
            PedidoField.FECHA, PedidoField.FECHA_ENVIO, PedidoField.FECHA_ENTREGA -> { compose.onNodeWithTag(tag).performClick(); selectPedidoDate(compose, compose.activity, value) }
            PedidoField.ESTADO -> { compose.onNodeWithTag(tag).performClick(); menuItem(value).performClick() }
            else -> compose.onNodeWithTag(tag).performTextReplacement(value)
        }
    }
    private fun llenarTodoMenosEstado() {
        for ((field, value) in PedidoDraft.from(examplePedido()).values) if (field != PedidoField.ESTADO) enter(field, value)
    }

    @Test fun elSelectorMuestraExactamenteLasSeisOpciones() {
        val runtime = PedidosTestRuntime(); install(runtime); click("pedidoNuevo")
        click("pedidoField_ESTADO")
        val esperadas = listOf("Seleccione un estado", "Pendiente", "En preparación", "Enviado", "Entregado", "Cancelado")
        for (texto in esperadas) menuItem(texto).assertExists()
        val menu = compose.onNodeWithTag("pedidoEstadoMenu", useUnmergedTree = true)
        menu.assertExists()
        assertEquals(6, compose.onAllNodes(hasAnyAncestor(hasTestTag("pedidoEstadoMenu")) and hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun sinEstadoValidoElErrorApareceDebajoDelCampoYNoSeGuarda() {
        val runtime = PedidosTestRuntime(); install(runtime); click("pedidoNuevo")
        llenarTodoMenosEstado()
        click("pedidoGuardar")
        // El mensaje de error forma parte del campo (árbol sin fusionar): se desplaza al campo y se consulta ahí.
        show("pedidoField_ESTADO")
        compose.onNodeWithTag("pedidoError_ESTADO", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, (runtime.pedidosRepository as UiPedidos).writes) }
        // «Seleccione un estado» sigue siendo inválido.
        enter(PedidoField.ESTADO, "Seleccione un estado"); click("pedidoGuardar")
        compose.onNodeWithTag("pedidoError_ESTADO", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(0, (runtime.pedidosRepository as UiPedidos).writes) }
        // Un estado de la lista quita el error y permite guardar.
        enter(PedidoField.ESTADO, "Entregado")
        compose.onNodeWithTag("pedidoError_ESTADO", useUnmergedTree = true).assertDoesNotExist()
        click("pedidoGuardar")
        compose.runOnIdle { assertEquals(1, (runtime.pedidosRepository as UiPedidos).writes) }
    }

    @Test fun siFallaLaConsultaInicialSeMuestraErrorConReintentoYNoUnaListaVacia() {
        val runtime = PedidosTestRuntime()
        (runtime.pedidosRepository as UiPedidos).readError = AppError.ErrorConexion
        install(runtime)
        compose.onNodeWithTag("pedidosError").assertIsDisplayed()
        compose.onNodeWithTag("pedidosReintentar").assertIsDisplayed()
        compose.onAllNodesWithText("Todavía no hay pedidos.").assertCountEquals(0)
        (runtime.pedidosRepository as UiPedidos).readError = null
        compose.onNodeWithTag("pedidosReintentar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pedidoRow_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("pedidosReintentar").assertCountEquals(0)
        compose.onAllNodesWithTag("pedidosError").assertCountEquals(0)
    }

    @Test fun unaListaVaciaCargadaCorrectamenteSiMuestraElMensajeDeVacio() {
        val runtime = PedidosTestRuntime()
        (runtime.pedidosRepository as UiPedidos).items = emptyList()
        install(runtime)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Todavía no hay pedidos.").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("pedidosReintentar").assertCountEquals(0)
    }
}
