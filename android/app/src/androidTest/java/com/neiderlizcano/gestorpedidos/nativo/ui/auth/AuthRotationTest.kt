package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class AuthRotationTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun install(runtime:UiRuntime){compose.activityRule.scenario.onActivity{it.setContent{AuthRoute(runtime)}};compose.waitForIdle()}
 @Test fun loginFieldsSurviveRecreationAndAreClearedWhenStartingRecovery(){
  val runtime=UiRuntime();install(runtime)
  compose.onNodeWithTag("authEmail").performTextInput("a@example.invalid")
  compose.onNodeWithTag("authPassword").performTextInput("clave-ficticia")
  compose.activityRule.scenario.recreate();install(runtime)
  compose.onNodeWithTag("authEmail").assertTextContains("a@example.invalid")
  compose.onNodeWithTag("authPassword").assertTextContains("clave-ficticia")
  compose.onNodeWithTag("authRecover").performScrollTo().performClick()
  compose.onNodeWithTag("authEmail").assert(SemanticsMatcher("Campo vacío") { it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty() })
  compose.onNodeWithTag("authPassword").assertDoesNotExist()
 }
}
