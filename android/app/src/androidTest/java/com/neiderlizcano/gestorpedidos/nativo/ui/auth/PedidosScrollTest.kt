package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class PedidosScrollTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun install(runtime:PedidosTestRuntime){
  compose.activityRule.scenario.onActivity{a->a.setContent{GestorPedidosTheme{Box(Modifier.width(320.dp).height(600.dp)){AuthRoute(runtime)}}}}
  compose.waitForIdle();compose.abrirPedidos()
 }
 private fun show(tag:String){compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag(tag))}
 private fun click(tag:String){show(tag);compose.onNodeWithTag(tag).performClick()}
 @Test fun longListAndDetailScrollToBottomWithoutRedundantHeading(){
  val runtime=PedidosTestRuntime();(runtime.pedidosRepository as UiPedidos).items=(1L..40L).map{examplePedido().copy(id=it)}
  install(runtime)
  compose.onAllNodesWithText("Gestor de Pedidos").assertCountEquals(0)
  compose.onNodeWithTag("pedidosScreen").performTouchInput { swipeUp(durationMillis=600) }
  val range=compose.onNodeWithTag("pedidosScreen").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
  assertTrue(range.value()>0f)
  show("pedidoRow_40");compose.onNodeWithTag("pedidoRow_40").assertIsDisplayed().performClick()
  show("pedidoEliminar");compose.onNodeWithTag("pedidoEliminar").assertIsDisplayed()
  show("pedidosVolver");compose.onNodeWithTag("pedidosVolver").assertIsDisplayed()
  compose.onAllNodesWithText("Gestor de Pedidos").assertCountEquals(0)
 }
 @Test fun createAndEditReachLastFieldSaveAndBackWithKeyboardAndRetainDraftOnRotation(){
  val runtime=PedidosTestRuntime();install(runtime);click("pedidoNuevo")
  show("pedidoField_CLIENTE");compose.onNodeWithTag("pedidoField_CLIENTE").performTextInput("Borrador conservado")
  show("pedidoField_PROPINA");compose.onNodeWithTag("pedidoField_PROPINA").performClick().performTextInput("0")
  compose.waitUntil(5000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
  show("pedidoGuardar");compose.onNodeWithTag("pedidoGuardar").assertIsDisplayed().assertIsEnabled()
  show("pedidosVolver");compose.onNodeWithTag("pedidosVolver").assertIsDisplayed()
  compose.activityRule.scenario.recreate();install(runtime)
  show("pedidoField_CLIENTE");compose.onNodeWithTag("pedidoField_CLIENTE").assertTextContains("Borrador conservado")
  click("pedidosVolver");click("pedidoRow_1");click("pedidoEditar")
  show("pedidoField_PROPINA");compose.onNodeWithTag("pedidoField_PROPINA").performClick().performTextReplacement("1.00")
  compose.waitUntil(5000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
  compose.waitForIdle()
  show("pedidoGuardar");compose.onNodeWithTag("pedidoGuardar").assertIsDisplayed().performClick()
  show("pedidoId");compose.onNodeWithTag("pedidoId").assertTextContains("1",substring=true)
  compose.runOnIdle{assertEquals(1,(runtime.pedidosRepository as UiPedidos).writes)}
 }
}
