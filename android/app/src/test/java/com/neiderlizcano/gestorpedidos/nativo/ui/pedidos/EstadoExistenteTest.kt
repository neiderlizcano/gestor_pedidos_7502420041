package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class EstadoExistenteTest {
    @Test fun editarOtroCampoConservaExactamenteElEstadoDesconocidoRecibido() = runTest {
        val repo = FakePedidos().apply { data = listOf(samplePedido().copy(estado = "En reparto")) }
        val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); vm.consultar(1); runCurrent(); vm.editar()
        vm.cambiar(PedidoField.VALOR, "120.50"); vm.guardar(); runCurrent()
        assertTrue(vm.uiState.value.issues.isEmpty())
        assertEquals(1, repo.writes)
        assertEquals("En reparto", repo.data.single().estado)
        assertEquals(BigDecimal("120.50"), repo.data.single().valor)
    }

    @Test fun sePuedeElegirUnEstadoDelCatalogoYVolverAlOriginalSinPerderlo() {
        val draft = PedidoDraft.from(samplePedido().copy(estado = "  Preparación especial  "))
        assertTrue(draft.issues().isEmpty())
        assertTrue(draft.with(PedidoField.ESTADO, "Entregado").issues().isEmpty())
        assertTrue(draft.with(PedidoField.ESTADO, draft.estadoOriginal!!).issues().isEmpty())
        assertTrue(draft.estadoOriginal in opcionesEstado(draft[PedidoField.ESTADO]))
        assertEquals(FieldIssue.ESTADO_INVALIDO, draft.with(PedidoField.ESTADO, "Inventado").issues()[PedidoField.ESTADO])
        assertEquals(FieldIssue.ESTADO_INVALIDO, draft.with(PedidoField.ESTADO, " ").issues()[PedidoField.ESTADO])
    }

    @Test fun nuevoPedidoNoHeredaLaExcepcionDelEstadoAnterior() = runTest {
        val repo = FakePedidos().apply { data = listOf(samplePedido().copy(estado = "En reparto")) }
        val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); vm.consultar(1); runCurrent(); vm.editar(); vm.nuevo()
        assertNull(vm.uiState.value.draft.estadoOriginal)
        for ((field, value) in PedidoDraft.from(repo.data.single()).values) vm.cambiar(field, value)
        vm.guardar(); runCurrent()
        assertEquals(FieldIssue.ESTADO_INVALIDO, vm.uiState.value.issues[PedidoField.ESTADO])
        assertEquals(0, repo.writes)
        assertEquals(OPCIONES_ESTADO, opcionesEstado(""))
    }
}
