package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class ManualAuthRegressionTest {
 @Test fun emptyLoginMarksBothFieldsAndEditingClearsOnlyItsError() = runTest {
  val repo=FakeUiAuth(); val vm=AuthViewModel(repo,{ResultadoAuth.Exito(Unit)},backgroundScope);runCurrent()
  vm.login("","");assertEquals(setOf(AuthValidation.EMAIL,AuthValidation.PASSWORD),vm.uiState.value.invalidFields)
  assertEquals(0,repo.logins)
  vm.corregirCampo(AuthValidation.PASSWORD);assertEquals(setOf(AuthValidation.EMAIL),vm.uiState.value.invalidFields)
 }
 @Test fun mismatchCanBeCorrectedAndSavedOnceWithoutConsumingRecovery() = runTest {
  val base=FakeUiAuth(); var updates=0
  val repo=object: AuthRepository by base {
   override suspend fun actualizarPassword(id:RecuperacionId,nuevaPassword:String):ResultadoAuth<Unit>{updates++;return ResultadoAuth.Exito(Unit)}
  }
  val vm=AuthViewModel(repo,{ResultadoAuth.Exito(Unit)},backgroundScope);runCurrent()
  vm.recuperar();vm.solicitar("a@example.invalid");runCurrent();vm.verificar("codigo");runCurrent()
  vm.actualizar("clave","distinta");assertFalse(vm.uiState.value.busy);assertEquals(0,updates)
  vm.corregirCampo(AuthValidation.CONFIRMACION);vm.actualizar("clave","clave");vm.actualizar("clave","clave");runCurrent()
  assertEquals(1,updates);assertEquals(AuthStep.COMPLETADA,vm.uiState.value.step)
 }
 @Test fun retainedFieldsClearOnFlowAndAccountBoundary() = runTest {
  val repo=FakeUiAuth();val vm=AuthViewModel(repo,{ResultadoAuth.Exito(Unit)},backgroundScope);runCurrent()
  val form=vm.formMemory.obtain(AuthStep.LOGIN);form.password.value="clave-ficticia"
  assertSame(form,vm.formMemory.obtain(AuthStep.LOGIN));assertEquals("clave-ficticia",form.password.value)
  vm.formMemory.obtain(AuthStep.CORREO);assertEquals("",form.password.value)
  form.email.value="a@example.invalid";repo.estadoSesion.value=EstadoSesion.Autenticada("A");runCurrent()
  assertEquals("",form.email.value)
 }

 @Test fun missingRecoveryContextNeverSilentlyIgnoresSave() = runTest {
  val vm=AuthViewModel(FakeUiAuth(),{ResultadoAuth.Exito(Unit)},backgroundScope);runCurrent()
  vm.actualizar("clave","clave")
  assertEquals(AuthStep.CORREO,vm.uiState.value.step)
  assertEquals(ErrorAuth.OTP_INVALIDO_O_EXPIRADO,vm.uiState.value.error)
  assertFalse(vm.uiState.value.busy)
 }

}
