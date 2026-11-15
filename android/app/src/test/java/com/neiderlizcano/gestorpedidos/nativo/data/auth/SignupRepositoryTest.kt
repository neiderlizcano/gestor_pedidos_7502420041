package com.neiderlizcano.gestorpedidos.nativo.data.auth
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import com.neiderlizcano.gestorpedidos.nativo.core.session.SesionInterna
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.time.Clock
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class SignupRepositoryTest {
 @Test fun installsOnlySessionActuallyReturnedBySignup() = runTest {
  for (hasSession in listOf(false,true)) {
   val store = MemoryStore()
   val remote = object : AuthRemote by FakeAuthRemote() {
    override suspend fun signup(email:String,password:String): ResultadoAuth<SesionInterna?> = ResultadoAuth.Exito(if(hasSession) session(Clock.systemUTC()) else null)
   }
   val manager = CoordinatedSessionManager(remote,store,backgroundScope)
   val repo = RestAuthRepository(remote,manager)
   assertEquals(ResultadoAuth.Exito(!hasSession),repo.registrar("a@example.invalid","clave-de-prueba"))
   assertEquals(hasSession,store.saved != null)
   assertEquals(if(hasSession) EstadoSesion.Autenticada(AUTH_USER) else EstadoSesion.SinSesion,repo.estadoSesion.value)
  }
 }
 @Test fun lateSignupCannotRestoreSessionAfterLogout() = runTest {
  val store = MemoryStore(); val release = CompletableDeferred<Unit>()
  val remote = object : AuthRemote by FakeAuthRemote() {
   override suspend fun signup(email:String,password:String): ResultadoAuth<SesionInterna?> { release.await(); return ResultadoAuth.Exito(session(Clock.systemUTC())) }
  }
  val manager = CoordinatedSessionManager(remote,store,backgroundScope)
  val repo = RestAuthRepository(remote,manager)
  val result = backgroundScope.async { repo.registrar("a@example.invalid","clave-de-prueba") }
  runCurrent(); manager.cerrarSesion(); release.complete(Unit); runCurrent()
  assertTrue(result.await() is ResultadoAuth.Fallo)
  assertNull(store.saved); assertEquals(EstadoSesion.SinSesion,repo.estadoSesion.value)
 }
}
