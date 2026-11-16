package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.PedidoDraft
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.PedidoField
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// Menú central tras iniciar sesión, mensajes de éxito y confirmación de eliminación.
class MenuPrincipalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun install(runtime: PedidosTestRuntime) {
        compose.activityRule.scenario.onActivity { it.setContent { GestorPedidosTheme { AuthRoute(runtime) } } }
        compose.waitForIdle()
    }
    private fun show(tag: String) { compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag(tag)) }
    private fun click(tag: String) { show(tag); compose.onNodeWithTag(tag).performClick() }
    private fun enter(field: PedidoField, value: String) {
        val tag = "pedidoField_" + field.name
        show(tag)
        when (field) {
            PedidoField.FECHA, PedidoField.FECHA_ENVIO, PedidoField.FECHA_ENTREGA -> { compose.onNodeWithTag(tag).performClick(); selectPedidoDate(compose, compose.activity, value) }
            PedidoField.ESTADO -> { compose.onNodeWithTag(tag).performClick()
                compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("pedidoEstadoMenu")), useUnmergedTree = true).performClick() }
            else -> compose.onNodeWithTag(tag).performTextReplacement(value)
        }
    }

    @Test fun elMenuMuestraLasOpcionesYLosModulosF4EstanHabilitados() {
        install(PedidosTestRuntime())
        compose.onNodeWithTag("menuPrincipal").assertIsDisplayed()
        for (tag in listOf("navPedidos", "navNuevo", "navPerfil", "authLogout")) compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("navReportes").assertIsEnabled(); compose.onNodeWithTag("navSensores").assertIsEnabled()
        compose.onAllNodesWithText("Próximamente").assertCountEquals(0)
        // Sin encabezados duplicados: no hay barra de regreso en el menú.
        compose.onAllNodesWithTag("navInicio").assertCountEquals(0)
    }

    @Test fun elBotonUsarMiUbicacionSoloExisteEnCrearYActualizarPedido() {
        install(PedidosTestRuntime())
        // Menú, listado y detalle no lo muestran.
        compose.onAllNodesWithTag("ubicacionUsar").assertCountEquals(0)
        compose.onNodeWithTag("navPedidos").performClick()
        compose.onAllNodesWithTag("ubicacionUsar").assertCountEquals(0)
        compose.onNodeWithTag("pedidoRow_1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pedidoEditar").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("ubicacionUsar").assertCountEquals(0)
        // Actualizar pedido sí lo muestra, junto a la dirección.
        compose.onNodeWithTag("pedidoEditar").performClick()
        show("ubicacionUsar"); compose.onNodeWithTag("ubicacionUsar").assertIsDisplayed()
        // Crear pedido también.
        compose.onNodeWithTag("navInicio").performClick()
        compose.onNodeWithTag("navNuevo").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pedidoField_FECHA").fetchSemanticsNodes().isNotEmpty() }
        show("ubicacionUsar"); compose.onNodeWithTag("ubicacionUsar").assertIsDisplayed()
        // El perfil no lo muestra.
        compose.onNodeWithTag("navInicio").performClick()
        compose.onNodeWithTag("navPerfil").performClick()
        compose.onAllNodesWithTag("ubicacionUsar").assertCountEquals(0)
    }

    @Test fun pedidosYInicioNavegaSinDuplicarFilas() {
        install(PedidosTestRuntime())
        compose.onNodeWithTag("navPedidos").performClick()
        compose.onNodeWithTag("pedidosScreen").assertIsDisplayed()
        compose.onAllNodesWithTag("authLogout").assertCountEquals(0)
        compose.onAllNodesWithTag("navInicio").assertCountEquals(1)
        compose.onNodeWithTag("navInicio").performClick()
        compose.onNodeWithTag("menuPrincipal").assertIsDisplayed()
    }

    @Test fun nuevoPedidoAbreElFormularioDirectamente() {
        install(PedidosTestRuntime())
        compose.onNodeWithTag("navNuevo").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pedidoField_FECHA").fetchSemanticsNodes().isNotEmpty() }
        show("pedidoField_CLIENTE"); compose.onNodeWithTag("pedidoField_CLIENTE").assertIsDisplayed()
        compose.onAllNodesWithTag("pedidoNuevo").assertCountEquals(0)
    }

    @Test fun guardarMuestraConfirmacionYEliminarPideConfirmacionAntesDeBorrar() {
        val runtime = PedidosTestRuntime(); install(runtime)
        compose.onNodeWithTag("navNuevo").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pedidoField_FECHA").fetchSemanticsNodes().isNotEmpty() }
        for ((field, value) in PedidoDraft.from(examplePedido()).values) enter(field, value)
        click("pedidoGuardar")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pedidosExito").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pedidosExito").assertTextEquals("Pedido creado correctamente.")
        val repo = runtime.pedidosRepository as UiPedidos
        compose.runOnIdle { assertEquals(1, repo.writes) }
        // Eliminar: el diálogo permite cancelar sin modificar nada.
        click("pedidoEliminar")
        compose.onNodeWithTag("pedidoConfirmarEliminar").assertIsDisplayed()
        compose.onNodeWithTag("pedidoCancelarEliminar").performClick()
        compose.onAllNodesWithTag("pedidoConfirmarEliminar").assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, repo.writes) }
        // Al confirmar se elimina y se informa.
        click("pedidoEliminar"); compose.onNodeWithTag("pedidoConfirmarEliminar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pedidosExito").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pedidosExito").assertTextEquals("Pedido eliminado correctamente.")
        compose.onAllNodesWithTag("pedidoRow_2").assertCountEquals(0)
        compose.runOnIdle { assertEquals(2, repo.writes) }
    }
}
