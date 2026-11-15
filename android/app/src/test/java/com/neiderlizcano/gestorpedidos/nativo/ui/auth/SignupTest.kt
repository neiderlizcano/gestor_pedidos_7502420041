package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class SignupTest {
 @Test fun validatesBeforeSendingAndPreventsDoubleSubmission() = runTest {
  val base = FakeUiAuth(); var calls = 0; val release = CompletableDeferred<Unit>()
  val repo = object : AuthRepository by base {
   override suspend fun registrar(email: String, password: String): ResultadoAuth<Boolean> { calls++; release.await(); return ResultadoAuth.Exito(true) }
  }
  val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope); runCurrent(); vm.abrirRegistro()
  vm.registrar("incorrecto", "clave", "clave"); assertEquals(0,calls)
  vm.registrar("a@example.invalid", "clave", "otra"); assertEquals(AuthValidation.CONFIRMACION,vm.uiState.value.validation)
  vm.registrar("a@example.invalid", "clave", "clave"); runCurrent()
  vm.registrar("a@example.invalid", "clave", "clave"); runCurrent(); assertEquals(1,calls)
  release.complete(Unit); runCurrent(); assertEquals(AuthStep.REGISTRO_ENVIADO,vm.uiState.value.step)
  assertEquals(EstadoSesion.SinSesion,vm.uiState.value.sesion)
 }
}
