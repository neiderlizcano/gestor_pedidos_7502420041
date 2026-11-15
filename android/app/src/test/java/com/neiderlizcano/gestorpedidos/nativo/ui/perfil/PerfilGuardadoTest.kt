package com.neiderlizcano.gestorpedidos.nativo.ui.perfil

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// OBS-11 aplicado al perfil: confirmación de éxito solo si la escritura fue correcta.
@OptIn(ExperimentalCoroutinesApi::class)
class PerfilGuardadoTest {
    @Test fun guardarCorrectamenteMarcaGuardadoYEditarDeNuevoLoLimpia() = runTest {
        val repo = FakePerfiles(); val vm = PerfilViewModel(repo, {}, backgroundScope); runCurrent()
        vm.editar(); vm.cambiarUsername("nuevo"); vm.cambiarNombre("Nombre nuevo"); vm.guardar(); runCurrent()
        assertTrue(vm.uiState.value.guardado); assertFalse(vm.uiState.value.editing)
        vm.editar(); assertFalse(vm.uiState.value.guardado)
    }

    @Test fun unFalloDeEscrituraNoMarcaGuardado() = runTest {
        val repo = FakePerfiles(); repo.writeError = AppError.SesionNoValida
        val vm = PerfilViewModel(repo, {}, backgroundScope); runCurrent()
        vm.editar(); vm.cambiarUsername("nuevo"); vm.cambiarNombre("Nombre nuevo"); vm.guardar(); runCurrent()
        assertFalse(vm.uiState.value.guardado); assertEquals(1, repo.updates)
    }
}
