package com.neiderlizcano.gestorpedidos.nativo.ui.perfil

import androidx.lifecycle.ViewModelStore
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal class FakePerfiles : UsuariosRepository {
    var perfil: Perfil? = Perfil("A", "usuarioA", "Persona A", "a@example.invalid")
    var readError: AppError? = null
    var writeError: AppError? = null
    var creates = 0; var updates = 0; var deletes = 0
    var release: CompletableDeferred<Unit>? = null
    override suspend fun consultarPerfilPropio(): AppResult<Perfil> = readError?.let { AppResult.Fallo(it) }
        ?: perfil?.let { AppResult.Exito(it) } ?: AppResult.Fallo(AppError.ErrorHttp(404))
    private suspend fun write(datos: DatosPerfil): AppResult<Perfil> {
        release?.await(); writeError?.let { return AppResult.Fallo(it) }
        val result = Perfil("A", datos.username, datos.nombre, "a@example.invalid")
        perfil = result; return AppResult.Exito(result)
    }
    override suspend fun crearPerfil(datos: DatosPerfil): AppResult<Perfil> { creates++; return write(datos) }
    override suspend fun actualizarPerfilPropio(datos: DatosPerfil): AppResult<Perfil> { updates++; return write(datos) }
    override suspend fun eliminarCuentaPropia(): AppResult<Unit> {
        deletes++; release?.await(); writeError?.let { return AppResult.Fallo(it) }
        perfil = null; return AppResult.Exito(Unit)
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {
    private fun fill(vm: PerfilViewModel) { vm.cambiarUsername("nuevo"); vm.cambiarNombre("Nombre nuevo") }
    @Test fun missingProfileRequiresExplicitCreateAndRequiredFields() = runTest {
        val repo = FakePerfiles().apply { perfil = null }; val vm = PerfilViewModel(repo, {}, backgroundScope)
        runCurrent(); assertTrue(vm.uiState.value.missing); assertFalse(vm.uiState.value.editing)
        vm.guardar(); runCurrent(); assertEquals(0, repo.creates)
        vm.crear(); vm.guardar(); assertTrue(vm.uiState.value.usernameRequired); assertTrue(vm.uiState.value.nombreRequired)
        fill(vm); vm.guardar(); runCurrent()
        assertEquals(1, repo.creates); assertFalse(vm.uiState.value.missing); assertFalse(vm.uiState.value.editing)
        assertEquals("a@example.invalid", vm.uiState.value.perfil!!.email)
    }
    @Test fun readFailuresNeverEnableCreationOrInventMissingProfile() = runTest {
        for (error in listOf(AppError.ServicioNoDisponible, AppError.SesionNoValida, AppError.ErrorConexion)) {
            val repo = FakePerfiles().apply { readError = error }; val vm = PerfilViewModel(repo, {}, backgroundScope)
            runCurrent(); vm.crear(); fill(vm); vm.guardar(); runCurrent()
            assertFalse(vm.uiState.value.missing); assertFalse(vm.uiState.value.editing)
            assertEquals(error, vm.uiState.value.error); assertEquals(0, repo.creates)
        }
    }
    @Test fun editOnlySendsNameAndUsernameAndKeepsTrustedEmail() = runTest {
        val repo = FakePerfiles(); val vm = PerfilViewModel(repo, {}, backgroundScope); runCurrent()
        vm.editar(); vm.cambiarNombre(" "); vm.guardar(); runCurrent(); assertEquals(0, repo.updates)
        fill(vm); vm.guardar(); runCurrent()
        assertEquals(1, repo.updates); assertEquals(0, repo.creates)
        assertEquals(Perfil("A", "nuevo", "Nombre nuevo", "a@example.invalid"), vm.uiState.value.perfil)
    }
    @Test fun uncertainCreationThenFoundProfileBecomesUpdateWithoutDuplicatePost() = runTest {
        val repo = FakePerfiles().apply { perfil = null; writeError = AppError.RespuestaInvalida }
        val vm = PerfilViewModel(repo, {}, backgroundScope); runCurrent(); vm.crear(); fill(vm); vm.guardar(); runCurrent()
        assertEquals(EscrituraPerfil.CREAR, vm.uiState.value.uncertain)
        vm.guardar(); runCurrent(); assertEquals(1, repo.creates)
        repo.perfil = Perfil("A", "nuevo", "Nombre nuevo", "a@example.invalid"); repo.writeError = null
        vm.consultar(); runCurrent(); assertNull(vm.uiState.value.uncertain)
        vm.guardar(); runCurrent(); assertEquals(1, repo.creates); assertEquals(1, repo.updates)
    }
    @Test fun failedCheckDoesNotUnlockUpdateOrEraseDraft() = runTest {
        val repo = FakePerfiles(); val vm = PerfilViewModel(repo, {}, backgroundScope); runCurrent(); vm.editar(); fill(vm)
        repo.writeError = AppError.ErrorConexion; vm.guardar(); runCurrent()
        repo.readError = AppError.ServicioNoDisponible; vm.consultar(); runCurrent(); vm.guardar(); runCurrent()
        assertEquals(EscrituraPerfil.ACTUALIZAR, vm.uiState.value.uncertain)
        assertEquals("Nombre nuevo", vm.uiState.value.nombre); assertEquals(1, repo.updates)
        repo.readError = null; repo.writeError = null; vm.consultar(); runCurrent(); vm.guardar(); runCurrent()
        assertEquals(2, repo.updates); assertNull(vm.uiState.value.uncertain)
    }
    @Test fun deleteRequiresConfirmationAndSuccessClearsPrivateStateAndCallsLogoutOnce() = runTest {
        val repo = FakePerfiles(); var callbacks = 0; val vm = PerfilViewModel(repo, { callbacks++ }, backgroundScope); runCurrent()
        vm.eliminar(); runCurrent(); assertEquals(0, repo.deletes)
        vm.pedirEliminar(); vm.cancelarEliminar(); vm.eliminar(); runCurrent(); assertEquals(0, repo.deletes)
        vm.pedirEliminar(); vm.eliminar(); runCurrent(); vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertEquals(1, repo.deletes); assertEquals(1, callbacks); assertTrue(vm.uiState.value.deleted)
        assertNull(vm.uiState.value.perfil); assertEquals("", vm.uiState.value.nombre)
    }
    @Test fun expiredSessionOnWritingPreservesDraftAndNeverTriggersLogout() = runTest {
        val repo = FakePerfiles(); var callbacks = 0; val vm = PerfilViewModel(repo, { callbacks++ }, backgroundScope); runCurrent()
        vm.editar(); fill(vm); repo.writeError = AppError.SesionNoValida; vm.guardar(); runCurrent()
        assertTrue(vm.uiState.value.editing); assertEquals("Nombre nuevo", vm.uiState.value.nombre)
        assertEquals(AppError.SesionNoValida, vm.uiState.value.error); assertEquals(0, callbacks)
        vm.cancelar(); vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertEquals(0, callbacks); assertFalse(vm.uiState.value.deleted)
    }
    @Test fun uncertainDeletionCannotBeRepeatedEvenAfterProfile200Or404() = runTest {
        val repo = FakePerfiles(); var callbacks = 0; val vm = PerfilViewModel(repo, { callbacks++ }, backgroundScope); runCurrent()
        repo.writeError = AppError.ErrorConexion; vm.pedirEliminar(); vm.eliminar(); runCurrent()
        repo.writeError = null; vm.consultar(); runCurrent(); vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertEquals(EscrituraPerfil.ELIMINAR, vm.uiState.value.uncertain)
        repo.perfil = null; vm.consultar(); runCurrent(); vm.crear(); vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertTrue(vm.uiState.value.missing); assertFalse(vm.uiState.value.editing)
        assertEquals(1, repo.deletes); assertEquals(0, repo.creates); assertEquals(0, callbacks)
    }
    @Test fun busyPreventsDuplicateSavesAndDeletes() = runTest {
        val repo = FakePerfiles(); val vm = PerfilViewModel(repo, {}, backgroundScope); runCurrent(); vm.editar(); fill(vm)
        repo.release = CompletableDeferred(); vm.guardar(); runCurrent(); vm.guardar(); runCurrent(); assertEquals(1, repo.updates)
        repo.release!!.complete(Unit); runCurrent(); repo.release = CompletableDeferred()
        vm.pedirEliminar(); vm.eliminar(); runCurrent(); vm.eliminar(); runCurrent(); assertEquals(1, repo.deletes)
        repo.release!!.complete(Unit); runCurrent(); assertTrue(vm.uiState.value.deleted)
    }
    @Test fun clearingSessionStoreCancelsLateDeletionCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = FakePerfiles(); var callbacks = 0; val vm = PerfilViewModel(repo, { callbacks++ })
            val store = ViewModelStore(); store.put("perfil", vm); runCurrent()
            repo.release = CompletableDeferred(); vm.pedirEliminar(); vm.eliminar(); runCurrent(); store.clear()
            repo.release!!.complete(Unit); runCurrent(); assertEquals(0, callbacks)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun missingProfileDuringEditRequiresExplicitCreationInsteadOfImplicitPost() = runTest {
        val repo = FakePerfiles(); val vm = PerfilViewModel(repo, {}, backgroundScope); runCurrent()
        vm.editar(); fill(vm); repo.perfil = null; vm.consultar(); runCurrent()
        assertTrue(vm.uiState.value.missing); assertFalse(vm.uiState.value.editing)
        vm.guardar(); runCurrent(); assertEquals(0, repo.creates)
        vm.crear(); vm.guardar(); runCurrent(); assertEquals(1, repo.creates)
    }

}
