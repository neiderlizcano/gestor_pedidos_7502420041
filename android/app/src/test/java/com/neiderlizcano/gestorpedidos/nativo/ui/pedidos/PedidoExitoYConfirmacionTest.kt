package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// OBS-11 y OBS-12: confirmación de éxito, un solo envío por pulsaciones repetidas y eliminación solo tras confirmar.
@OptIn(ExperimentalCoroutinesApi::class)
class PedidoExitoYConfirmacionTest {
    private fun fill(vm: PedidosViewModel) { for ((f, v) in PedidoDraft.from(samplePedido()).values) vm.cambiar(f, v) }

    @Test fun guardarUnPedidoNuevoMarcaCreadoYMuestraElDetalleGuardado() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.nuevo(); fill(vm); vm.guardar(); runCurrent()
        assertEquals(ExitoPedido.CREADO, vm.uiState.value.exito)
        assertEquals(PedidosPage.DETALLE, vm.uiState.value.page); assertEquals(1, repo.writes)
        assertEquals(2, vm.uiState.value.items.size)
    }

    @Test fun actualizarMarcaActualizadoYEmpezarOtraOperacionLimpiaElMensaje() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.consultar(1); runCurrent(); vm.editar(); vm.cambiar(PedidoField.CLIENTE, "Cliente editado"); vm.guardar(); runCurrent()
        assertEquals(ExitoPedido.ACTUALIZADO, vm.uiState.value.exito)
        vm.cargar(); runCurrent()
        assertNull(vm.uiState.value.exito)
    }

    @Test fun pulsacionesRepetidasDeGuardarEnviaUnaSolaEscritura() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.nuevo(); fill(vm); repo.release = CompletableDeferred()
        vm.guardar(); runCurrent(); vm.guardar(); vm.guardar(); runCurrent()
        assertTrue(vm.uiState.value.busy); assertEquals(1, repo.writes)
        repo.release!!.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.busy); assertEquals(1, repo.writes)
    }

    @Test fun eliminarSinConfirmarNoHaceNadaYCancelarConservaElPedido() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.consultar(1); runCurrent()
        vm.eliminar(); runCurrent()
        assertEquals(0, repo.writes); assertEquals(1, vm.uiState.value.items.size)
        vm.pedirEliminar(); assertTrue(vm.uiState.value.confirmDelete)
        vm.cancelarEliminar(); runCurrent()
        assertFalse(vm.uiState.value.confirmDelete); assertEquals(0, repo.writes); assertEquals(1, vm.uiState.value.items.size)
        assertNull(vm.uiState.value.exito)
    }

    @Test fun confirmarLaEliminacionQuitaElPedidoYMuestraElResultado() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.consultar(1); runCurrent(); vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertEquals(ExitoPedido.ELIMINADO, vm.uiState.value.exito)
        assertTrue(vm.uiState.value.items.isEmpty()); assertEquals(PedidosPage.LISTA, vm.uiState.value.page)
        assertEquals(1, repo.writes)
    }
}
