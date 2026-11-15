package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.*
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

internal fun samplePedido(id: Long = 1) = Pedido(id, LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-16"),
    LocalDate.parse("2026-11-17"), "Cliente", "Proveedor", BigDecimal("100.25"), "Pendiente", "Colombia", "Bolívar", "Cartagena", "Calle ficticia", BigDecimal("0.00"))
internal class FakePedidos : PedidosRepository {
    var data = listOf(samplePedido())
    var writes = 0
    var writeError: AppError? = null
    var readError: AppError? = null
    var release: CompletableDeferred<Unit>? = null
    override suspend fun listar(): AppResult<List<Pedido>> = readError?.let { AppResult.Fallo(it) } ?: AppResult.Exito(data)
    override suspend fun consultar(id: Long): AppResult<Pedido> = AppResult.Exito(data.first { it.id == id })
    private suspend fun write(id: Long, d: DatosPedido): AppResult<Pedido> {
        writes++; release?.await()
        writeError?.let { return AppResult.Fallo(it) }
        val p = Pedido(id, d.fecha, d.fechaEnvio, d.fechaEntrega, d.cliente, d.proveedor, d.valor, d.estado, d.pais, d.departamento, d.ciudad, d.nomenclaturaVivienda, d.propina)
        data = data.filterNot { it.id == id } + p
        return AppResult.Exito(p)
    }
    override suspend fun crear(datos: DatosPedido) = write((data.maxOfOrNull { it.id } ?: 0) + 1, datos)
    override suspend fun actualizar(id: Long, datos: DatosPedido) = write(id, datos)
    override suspend fun eliminar(id: Long): AppResult<Unit> {
        writes++; writeError?.let { return AppResult.Fallo(it) }
        data = data.filterNot { it.id == id }; return AppResult.Exito(Unit)
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
class PedidosViewModelTest {
    private fun fill(vm: PedidosViewModel) { for ((f,v) in PedidoDraft.from(samplePedido()).values) vm.cambiar(f,v) }
    @Test fun completeCrudAndExactDecimalRoundTrip() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); assertEquals(1, vm.uiState.value.items.size)
        vm.nuevo(); fill(vm); vm.guardar(); runCurrent()
        assertEquals(PedidosPage.DETALLE, vm.uiState.value.page)
        assertEquals(2L, vm.uiState.value.selected!!.id)
        vm.editar(); vm.cambiar(PedidoField.VALOR, "1234567890.12"); vm.guardar(); runCurrent()
        assertEquals(BigDecimal("1234567890.12"), vm.uiState.value.selected!!.valor)
        vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertEquals(PedidosPage.LISTA, vm.uiState.value.page)
        assertEquals(1, repo.data.size)
    }
    @Test fun requiredAndFormatValidationNeverSendsInvalidRequest() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); vm.nuevo(); vm.guardar()
        assertEquals(12, vm.uiState.value.issues.size)
        fill(vm); vm.cambiar(PedidoField.FECHA, "2026-02-30"); vm.cambiar(PedidoField.PROPINA, "incorrecto"); vm.guardar()
        assertEquals(FieldIssue.FECHA_ISO, vm.uiState.value.issues[PedidoField.FECHA])
        assertEquals(FieldIssue.DECIMAL, vm.uiState.value.issues[PedidoField.PROPINA])
        assertEquals(0, repo.writes)
    }
    @Test fun rejectedWritingKeepsDraftAndNeverRetriesOrLogsOut() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); vm.nuevo(); fill(vm)
        repo.writeError = AppError.SesionNoValida
        vm.guardar(); runCurrent()
        assertEquals(1, repo.writes)
        assertEquals(PedidosPage.EDITOR, vm.uiState.value.page)
        assertEquals("Cliente", vm.uiState.value.draft[PedidoField.CLIENTE])
        assertEquals(AppError.SesionNoValida, vm.uiState.value.error)
        assertTrue(vm.uiState.value.writeFailure)
        repo.writeError = null; vm.guardar(); runCurrent()
        assertEquals(2, repo.writes)
        assertEquals(PedidosPage.DETALLE, vm.uiState.value.page)
    }
    @Test fun uncertainWriteDoesNotAutomaticallyReloadOrResend() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); vm.nuevo(); fill(vm); repo.writeError = AppError.ErrorConexion
        vm.guardar(); runCurrent()
        assertEquals(1, repo.writes); assertTrue(vm.uiState.value.dirty)
        vm.cargar(); runCurrent(); vm.continuar()
        assertEquals("Cliente", vm.uiState.value.draft[PedidoField.CLIENTE])
        assertEquals(1, repo.writes)
    }
    @Test fun busyGuardsAgainstDuplicateSave() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); vm.nuevo(); fill(vm); repo.release = CompletableDeferred()
        vm.guardar(); runCurrent(); vm.guardar(); runCurrent()
        assertEquals(1, repo.writes)
        repo.release!!.complete(Unit); runCurrent(); assertFalse(vm.uiState.value.busy)
    }
    @Test fun deleteNeedsConfirmationAndFailureDoesNotRemoveRecord() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope)
        runCurrent(); vm.consultar(1); runCurrent(); vm.eliminar(); runCurrent()
        assertEquals(0, repo.writes)
        repo.writeError = AppError.SesionNoValida; vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertEquals(PedidosPage.DETALLE, vm.uiState.value.page)
        assertEquals(1, repo.data.size)
    }
    @Test fun emptyAndFailedListsAreDistinct() = runTest {
        val repo = FakePedidos(); repo.data = emptyList()
        val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        assertTrue(vm.uiState.value.items.isEmpty()); assertNull(vm.uiState.value.error)
        repo.readError = AppError.ServicioNoDisponible; vm.cargar(); runCurrent()
        assertEquals(AppError.ServicioNoDisponible, vm.uiState.value.error)
    }
    @Test fun monetaryEndpointsAndEqualDatesAreAcceptedWithoutRounding() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        for (value in listOf("0.01", "9999999999.99", "10.500", "000000000010.50")) {
            vm.nuevo(); fill(vm)
            vm.cambiar(PedidoField.VALOR, value)
            vm.cambiar(PedidoField.PROPINA, if (value == "0.01") "0" else "9999999999.99")
            vm.cambiar(PedidoField.FECHA_ENVIO, "2026-11-15"); vm.cambiar(PedidoField.FECHA_ENTREGA, "2026-11-15")
            vm.guardar(); runCurrent()
            assertTrue(vm.uiState.value.issues.isEmpty())
            assertEquals(0, BigDecimal(value).compareTo(vm.uiState.value.selected!!.valor))
        }
        assertEquals(4, repo.writes)
    }
    @Test fun invalidMonetaryBoundariesIdentifyFieldAndNeverWrite() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        for (field in listOf(PedidoField.VALOR, PedidoField.PROPINA)) {
            val cases = listOf(
                "-0.01" to if (field == PedidoField.VALOR) FieldIssue.VALOR_POSITIVO else FieldIssue.PROPINA_NO_NEGATIVA,
                "0.001" to FieldIssue.MAXIMO_DECIMALES,
                "9999999999.999" to FieldIssue.MAXIMO_DECIMALES,
                "10000000000.00" to FieldIssue.MAXIMO_ENTEROS,
                "1e3" to FieldIssue.DECIMAL, "+5" to FieldIssue.DECIMAL,
                "NaN" to FieldIssue.DECIMAL, "1,50" to FieldIssue.DECIMAL
            ) + if (field == PedidoField.VALOR) listOf("0" to FieldIssue.VALOR_POSITIVO, "-0.00" to FieldIssue.VALOR_POSITIVO) else emptyList()
            for ((value, issue) in cases) {
                vm.nuevo(); fill(vm); vm.cambiar(field, value); vm.guardar(); runCurrent()
                assertEquals("$field: $value", mapOf(field to issue), vm.uiState.value.issues)
            }
        }
        assertEquals(0, repo.writes)
    }
    @Test fun reversedDatesHaveSpecificIssuesAndChangingBaseDateRevalidatesDependents() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.nuevo(); fill(vm); vm.cambiar(PedidoField.FECHA_ENVIO, "2026-11-14"); vm.cambiar(PedidoField.FECHA_ENTREGA, "2026-11-13")
        vm.guardar(); runCurrent()
        assertEquals(FieldIssue.ENVIO_ANTERIOR, vm.uiState.value.issues[PedidoField.FECHA_ENVIO])
        assertEquals(FieldIssue.ENTREGA_ANTERIOR, vm.uiState.value.issues[PedidoField.FECHA_ENTREGA])
        assertEquals(0, repo.writes)
        vm.cambiar(PedidoField.FECHA, "2026-11-14"); vm.cambiar(PedidoField.FECHA_ENTREGA, "2026-11-14")
        assertTrue(vm.uiState.value.issues.isEmpty())
        vm.guardar(); runCurrent(); assertEquals(1, repo.writes)
    }
    @Test fun malformedDateKeepsFormatErrorInsteadOfChronologyError() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.nuevo(); fill(vm); vm.cambiar(PedidoField.FECHA_ENVIO, "2026-02-30"); vm.guardar(); runCurrent()
        assertEquals(mapOf(PedidoField.FECHA_ENVIO to FieldIssue.FECHA_ISO), vm.uiState.value.issues)
        assertEquals(0, repo.writes)
    }
    @Test fun uncertainSaveStaysBlockedAfterFailedCheckAndUnlocksOnlyAfterSuccessfulList() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.nuevo(); fill(vm); repo.writeError = AppError.RespuestaInvalida; vm.guardar(); runCurrent()
        assertTrue(vm.uiState.value.needsReconciliation)
        repo.writeError = null; vm.cambiar(PedidoField.CLIENTE, "Borrador conservado"); vm.guardar(); runCurrent()
        assertEquals(1, repo.writes)
        repo.readError = AppError.ErrorConexion; vm.cargar(); runCurrent(); vm.guardar(); runCurrent()
        assertTrue(vm.uiState.value.needsReconciliation); assertEquals(1, repo.writes)
        repo.readError = null; vm.cargar(); runCurrent()
        assertFalse(vm.uiState.value.needsReconciliation)
        vm.continuar(); assertEquals("Borrador conservado", vm.uiState.value.draft[PedidoField.CLIENTE])
        assertEquals(1, repo.writes)
        vm.guardar(); runCurrent(); assertEquals(2, repo.writes)
    }
    @Test fun newFormOrDetailCannotBypassUncertainWriteGuard() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.nuevo(); fill(vm); repo.writeError = AppError.ErrorConexion; vm.guardar(); runCurrent()
        vm.nuevo(); fill(vm); vm.guardar(); runCurrent()
        vm.consultar(1); runCurrent(); vm.editar(); vm.guardar(); vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertTrue(vm.uiState.value.needsReconciliation); assertEquals(1, repo.writes)
    }
    @Test fun uncertainDeleteCannotRepeatUntilListHasBeenChecked() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        vm.consultar(1); runCurrent(); repo.writeError = AppError.ErrorConexion
        vm.pedirEliminar(); vm.eliminar(); runCurrent()
        repo.writeError = null; vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertTrue(vm.uiState.value.needsReconciliation); assertEquals(1, repo.writes)
        vm.cargar(); runCurrent(); vm.consultar(1); runCurrent(); vm.pedirEliminar(); vm.eliminar(); runCurrent()
        assertEquals(2, repo.writes); assertTrue(repo.data.isEmpty())
    }
    @Test fun uncertainHttpFailuresAlsoRequireCheckingButKnownRejectionsDoNot() = runTest {
        val repo = FakePedidos(); val vm = PedidosViewModel(repo, backgroundScope); runCurrent()
        for (error in listOf(AppError.ErrorHttp(500), AppError.ErrorHttp(408), AppError.ServicioNoDisponible)) {
            vm.nuevo(); fill(vm); repo.writeError = error; vm.guardar(); runCurrent()
            assertTrue(vm.uiState.value.needsReconciliation)
            val writes = repo.writes; vm.guardar(); runCurrent(); assertEquals(writes, repo.writes)
            vm.cargar(); runCurrent()
        }
        for (error in listOf(AppError.SesionNoValida, AppError.SolicitudInvalida, AppError.AccesoDenegado)) {
            vm.nuevo(); fill(vm); repo.writeError = error; vm.guardar(); runCurrent()
            assertFalse(vm.uiState.value.needsReconciliation)
            assertEquals(PedidosPage.EDITOR, vm.uiState.value.page)
        }
    }

}
