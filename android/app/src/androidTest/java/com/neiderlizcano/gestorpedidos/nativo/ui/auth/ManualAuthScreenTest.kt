package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.neiderlizcano.gestorpedidos.nativo.di.AuthRuntime
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class ManualAuthScreenTest {
 @get:Rule val compose=createComposeRule()
 private fun click(tag:String){compose.onNodeWithTag(tag).performScrollTo().performClick()}
 @Test fun emptyPasswordHasVisibleInlineErrorAndLoginIsCenteredWithGraphic() {
  compose.setContent{AuthRoute(UiRuntime())}
  click("authSubmit")
  compose.onNodeWithTag("authPassword").performScrollTo()
  compose.onNodeWithTag("authPasswordError",useUnmergedTree=true).assertTextEquals("Escribe tu contraseña.")
  compose.onNodeWithTag("authPassword").performTextInput("clave")
  compose.onNodeWithTag("authPasswordError",useUnmergedTree=true).assertDoesNotExist()
  compose.onNodeWithTag("authBrand").assertExists();compose.onNodeWithText("GP").assertDoesNotExist()
  val button=compose.onNodeWithTag("authSubmit").fetchSemanticsNode().boundsInRoot
  val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
  assertEquals(root.center.x,button.center.x,2f)
 }
 @Test fun invalidCredentialsAreClearWithoutDisclosingAccountExistence() {
  val base=UiRuntime()
  val runtime=object:AuthRuntime{
   override val authRepository=object:AuthRepository by base.authRepository{
    override suspend fun iniciarSesion(email:String,password:String):ResultadoAuth<Unit> = ResultadoAuth.Fallo(ErrorAuth.CREDENCIALES_RECHAZADAS)
   }
   override suspend fun inicializar()=base.inicializar()
  }
  compose.setContent{AuthRoute(runtime)}
  compose.onNodeWithTag("authEmail").performTextInput("a@example.invalid")
  compose.onNodeWithTag("authPassword").performTextInput("clave")
  click("authSubmit")
  compose.onNodeWithText("Correo o contraseña incorrectos. Revisa ambos datos e inténtalo de nuevo.").assertExists()
 }
 @Test fun recoveryMismatchThenCorrectionSavesWithoutAnotherOtp() {
  val base=UiRuntime();var updates=0
  val runtime=object:AuthRuntime{
   override val authRepository=object:AuthRepository by base.authRepository{
    override suspend fun actualizarPassword(id:RecuperacionId,nuevaPassword:String):ResultadoAuth<Unit>{updates++;return ResultadoAuth.Exito(Unit)}
   }
   override suspend fun inicializar()=base.inicializar()
  }
  compose.setContent{AuthRoute(runtime)};click("authRecover")
  compose.onNodeWithTag("authEmail").performTextInput("a@example.invalid");click("authSend")
  compose.onNodeWithTag("authCode").performTextInput("codigo");click("authVerify")
  compose.onNodeWithTag("authNewPassword").performScrollTo().performTextInput("clave-nueva")
  compose.onNodeWithTag("authConfirmation").performScrollTo().performTextInput("distinta");click("authUpdate")
  compose.onNodeWithTag("authConfirmationError",useUnmergedTree=true).assertExists()
  compose.runOnIdle{assertEquals(0,updates)}
  compose.onNodeWithTag("authConfirmation").performScrollTo().performTextReplacement("clave-nueva")
  compose.onNodeWithTag("authConfirmationError",useUnmergedTree=true).assertDoesNotExist()
  click("authUpdate");compose.onNodeWithTag("authReturnLogin").assertExists()
  compose.runOnIdle{assertEquals(1,updates)}
 }
 @Test fun localPasswordErrorAppearsExactlyOnceAndClearsOnCorrection() {
  val runtime=UiRuntime();compose.setContent{AuthRoute(runtime)}
  compose.onNodeWithTag("authEmail").performTextInput("a@example.invalid")
  click("authSubmit")
  compose.onAllNodesWithText("Escribe tu contraseña.",useUnmergedTree=true).assertCountEquals(1)
  compose.onNodeWithTag("authPasswordError",useUnmergedTree=true).assertExists()
  compose.onNodeWithTag("authError").assertDoesNotExist()
  compose.onNodeWithTag("authPassword").performScrollTo().performTextInput("clave-ficticia")
  compose.onAllNodesWithText("Escribe tu contraseña.",useUnmergedTree=true).assertCountEquals(0)
  compose.onNodeWithTag("authError").assertDoesNotExist()
 }

}
