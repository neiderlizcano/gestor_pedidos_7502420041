package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EstadoExistenteScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(tag: String) {
        compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun elegir(estado: String) {
        click("pedidoField_ESTADO")
        compose.onNode(hasText(estado) and hasAnyAncestor(hasTestTag("pedidoEstadoMenu")), useUnmergedTree = true).performClick()
    }

    @Test fun conservaElEstadoRealAlEditarValorInclusoTrasCambiarLaSeleccionYRotar() {
        val runtime = PedidosTestRuntime()
        val repo = runtime.pedidosRepository as UiPedidos
        repo.items = listOf(examplePedido().copy(estado = "En reparto"))
        fun instalar() {
            compose.activityRule.scenario.onActivity { it.setContent { AuthRoute(runtime) } }
            compose.waitForIdle(); compose.abrirPedidos()
        }
        instalar(); click("pedidoRow_1"); click("pedidoEditar")
        elegir("Entregado"); elegir("En reparto")
        compose.activityRule.scenario.recreate(); instalar()
        compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag("pedidoField_VALOR"))
        compose.onNodeWithTag("pedidoField_VALOR").performTextReplacement("200.25")
        click("pedidoGuardar")
        compose.runOnIdle {
            assertEquals(1, repo.writes)
            assertEquals("En reparto", repo.items.single().estado)
            assertEquals("200.25", repo.items.single().valor.toPlainString())
        }
    }
}
