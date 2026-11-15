package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.neiderlizcano.gestorpedidos.nativo.di.PublicConfiguration
import org.junit.Rule
import org.junit.Test
class PublicConfigurationScreenTest {
 @get:Rule val compose = createComposeRule()
 @Test fun invalidConfigurationShowsPropertyNamesWithoutValuesOrLogin() {
  val privateValue = "dato-ficticio-no-publico"
  val runtime = PublicConfiguration.initialize("http://proyecto.invalid/auth/v1/",privateValue,"") { _,_,_ -> error("No debe inicializar clientes") }
  compose.setContent { AuthRoute(runtime) }
  compose.onNodeWithText("Configuración pendiente").assertExists()
  compose.onNodeWithText("GESTOR_AUTH_URL",substring=true).assertExists()
  compose.onNodeWithText("GESTOR_AUTH_PUBLIC_KEY",substring=true).assertExists()
  compose.onNodeWithText("GESTOR_API_URL",substring=true).assertExists()
  compose.onNodeWithText(privateValue,substring=true).assertDoesNotExist()
  compose.onNodeWithTag("authSubmit").assertDoesNotExist()
 }
}
