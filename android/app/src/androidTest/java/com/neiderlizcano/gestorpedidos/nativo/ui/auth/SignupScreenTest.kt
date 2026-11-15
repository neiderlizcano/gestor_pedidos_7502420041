package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.neiderlizcano.gestorpedidos.nativo.di.AuthRuntime
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import org.junit.Rule
import org.junit.Test
class SignupScreenTest {
 @get:Rule val compose = createComposeRule()
 @Test fun registrationWithoutSessionReturnsToLogin() {
  val base = UiRuntime()
  val runtime = object : AuthRuntime {
   override val authRepository = object : AuthRepository by base.authRepository {
    override suspend fun registrar(email: String,password: String): ResultadoAuth<Boolean> = ResultadoAuth.Exito(true)
   }
   override suspend fun inicializar() = base.inicializar()
  }
  compose.setContent { AuthRoute(runtime) }
  compose.onNodeWithTag("authSignup").performScrollTo().performClick()
  compose.onNodeWithTag("signupEmail").performTextInput("a@example.invalid")
  compose.onNodeWithTag("signupPassword").performTextInput("clave-de-prueba")
  compose.onNodeWithTag("signupConfirmation").performTextInput("clave-de-prueba")
  compose.onNodeWithTag("signupSubmit").performScrollTo().performClick()
  compose.onNodeWithText("Solicitud recibida. Si tu cuenta requiere confirmación, revisa tu correo antes de iniciar sesión.").assertExists()
  compose.onNodeWithTag("authLogout").assertDoesNotExist()
  compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
  compose.onNodeWithTag("authSubmit").assertExists()
 }
}
