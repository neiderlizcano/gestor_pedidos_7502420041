package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Carga inicial tras entrar a la sesión: cargando -> lista, o error con reintento, sin estados intermedios engañosos.
@OptIn(ExperimentalCoroutinesApi::class)
class PedidoCargaInicialTest {
    @Test fun alCrearseMuestraCargandoSinMarcarLaListaComoCargadaNiVacia() = runTest {
        val repo = FakePedidos(); repo.release = null
        val compuerta = CompletableDeferred<Unit>()
        val lenta = object : com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.PedidosRepository by repo {
            override suspend fun listar(): com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult<List<com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.Pedido>> { compuerta.await(); return repo.listar() }
        }
        val vm = PedidosViewModel(lenta, backgroundScope)
        runCurrent()
        assertTrue(vm.uiState.value.busy); assertFalse(vm.uiState.value.cargado); assertNull(vm.uiState.value.error)
        compuerta.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.busy); assertTrue(vm.uiState.value.cargado)
        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test fun siFallaLaPrimeraConsultaHayErrorSinListaCargadaYSePuedeReintentar() = runTest {
        val repo = FakePedidos(); repo.readError = AppError.ErrorConexion
        val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        assertEquals(AppError.ErrorConexion, vm.uiState.value.error)
        assertFalse(vm.uiState.value.cargado); assertFalse(vm.uiState.value.busy)
        assertTrue(vm.uiState.value.items.isEmpty())
        repo.readError = null; vm.cargar(); runCurrent()
        assertNull(vm.uiState.value.error); assertTrue(vm.uiState.value.cargado)
        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test fun unaListaVaciaCargadaConExitoSiEsVaciaYSeDistingueDelFallo() = runTest {
        val repo = FakePedidos(); repo.data = emptyList()
        val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        assertTrue(vm.uiState.value.cargado); assertTrue(vm.uiState.value.items.isEmpty()); assertNull(vm.uiState.value.error)
    }

    @Test fun unFalloPosteriorNoDesmarcaLaListaYaCargada() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        repo.readError = AppError.ServicioNoDisponible; vm.cargar(); runCurrent()
        assertTrue(vm.uiState.value.cargado); assertEquals(1, vm.uiState.value.items.size)
        assertEquals(AppError.ServicioNoDisponible, vm.uiState.value.error)
    }

    @Test fun elegirEstadoLimpiaElErrorYElMarcadorVuelveAInvalidar() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.nuevo(); vm.guardar()
        assertEquals(FieldIssue.ESTADO_INVALIDO, vm.uiState.value.issues[PedidoField.ESTADO])
        vm.cambiar(PedidoField.ESTADO, "Enviado")
        assertNull(vm.uiState.value.issues[PedidoField.ESTADO])
        vm.cambiar(PedidoField.ESTADO, "")
        assertEquals(FieldIssue.ESTADO_INVALIDO, vm.uiState.value.issues[PedidoField.ESTADO])
    }
}
