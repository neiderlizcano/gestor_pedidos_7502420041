package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class PedidoCalendarTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun install(runtime:PedidosTestRuntime){compose.activityRule.scenario.onActivity{it.setContent{AuthRoute(runtime)}};compose.waitForIdle()}
 private fun show(tag:String){compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag(tag))}
 private fun click(tag:String){show(tag);compose.onNodeWithTag(tag).performClick()}
 private fun enter(field:PedidoField,value:String){
  val tag="pedidoField_"+field.name;show(tag)
  if(field in setOf(PedidoField.FECHA,PedidoField.FECHA_ENVIO,PedidoField.FECHA_ENTREGA)){
   compose.onNodeWithTag(tag).performClick();compose.onNodeWithTag("pedidoDatePicker").assertExists();selectPedidoDate(compose,compose.activity,value)
  }else if (field == PedidoField.ESTADO) {
      // Lista cerrada: se abre el selector y se elige la opción (no se escribe texto).
      compose.onNodeWithTag(tag).performClick()
      compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("pedidoEstadoMenu")), useUnmergedTree = true).performClick()
  } else compose.onNodeWithTag(tag).performTextReplacement(value)
 }
 @Test fun calendarsCancelAndConfirmThenCreateAndUpdateWithExistingStateSuggestion(){
  val runtime=PedidosTestRuntime();install(runtime);click("pedidoNuevo")
  click("pedidoField_FECHA");compose.onNodeWithTag("pedidoDateCancel").performClick()
  compose.onNodeWithTag("pedidoField_FECHA").assertTextContains("Seleccionar fecha",substring=true)
  for((field,value) in PedidoDraft.from(examplePedido()).values)enter(field,value)
  click("pedidoField_ESTADO");compose.onNode(hasText("Pendiente") and hasAnyAncestor(hasTestTag("pedidoEstadoMenu")),useUnmergedTree=true).performClick()
  click("pedidoGuardar");click("pedidoEditar");enter(PedidoField.FECHA_ENTREGA,"2026-11-18");click("pedidoGuardar")
  compose.runOnIdle { val repo=runtime.pedidosRepository as UiPedidos;assertEquals(2,repo.writes);assertEquals("2026-11-18",repo.items.last().fechaEntrega.toString()) }
 }
 @Test fun invalidSaveRevealsFirstFieldAndChronologyErrorsClearAfterCorrection(){
  val runtime=PedidosTestRuntime();install(runtime);click("pedidoNuevo");click("pedidoGuardar")
  compose.onNodeWithTag("pedidoField_FECHA").assertIsDisplayed()
  compose.runOnIdle{assertEquals(0,(runtime.pedidosRepository as UiPedidos).writes)}
  for((field,value) in PedidoDraft.from(examplePedido()).values)enter(field,value)
  enter(PedidoField.FECHA_ENVIO,"2026-11-14");click("pedidoGuardar")
  compose.onNodeWithTag("pedidoError_FECHA_ENVIO",useUnmergedTree=true).assertIsDisplayed()
  enter(PedidoField.FECHA_ENVIO,"2026-11-16")
  compose.onNodeWithTag("pedidoError_FECHA_ENVIO",useUnmergedTree=true).assertDoesNotExist()
  click("pedidoGuardar");compose.runOnIdle{assertEquals(1,(runtime.pedidosRepository as UiPedidos).writes)}
 }
}
