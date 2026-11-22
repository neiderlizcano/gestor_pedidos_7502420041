package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.*
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Campos editables del pedido; el identificador lo asigna el backend. */
enum class PedidoField { FECHA, FECHA_ENVIO, FECHA_ENTREGA, CLIENTE, PROVEEDOR, VALOR, ESTADO, PAIS, DEPARTAMENTO, CIUDAD, NOMENCLATURA, PROPINA }
/** Problemas de validación de un campo, que la pantalla traduce a mensajes. */
enum class FieldIssue {
    OBLIGATORIO, FECHA_ISO, DECIMAL, VALOR_POSITIVO, PROPINA_NO_NEGATIVA,
    MAXIMO_ENTEROS, MAXIMO_DECIMALES, ENVIO_ANTERIOR, ENTREGA_ANTERIOR, ESTADO_INVALIDO
}
/** Vista actual de pedidos: lista, detalle o editor. */
enum class PedidosPage { LISTA, DETALLE, EDITOR }
/** Borrador del formulario: texto de cada campo y, al editar, el estado original recibido del servidor. */
data class PedidoDraft(
    val values: Map<PedidoField, String> = emptyMap(),
    /** Estado recibido al editar. Se conserva literalmente aunque no forme parte del catálogo de creación. */
    val estadoOriginal: String? = null
) {
    operator fun get(field: PedidoField) = values[field].orEmpty()
    fun with(field: PedidoField, value: String) = copy(values = values + (field to value))
    fun issues(): Map<PedidoField, FieldIssue> = buildMap {
        val dates = mutableMapOf<PedidoField, LocalDate>()
        for (field in PedidoField.entries) {
            val value = this@PedidoDraft[field]
            when {
                // Crear usa cinco opciones; editar también permite conservar el estado real recibido de Spring.
                field == PedidoField.ESTADO -> if (value.isBlank() ||
                    (value !in ESTADOS_PEDIDO && value != estadoOriginal)) put(field, FieldIssue.ESTADO_INVALIDO)
                value.isBlank() -> put(field, FieldIssue.OBLIGATORIO)
                field in setOf(PedidoField.FECHA, PedidoField.FECHA_ENVIO, PedidoField.FECHA_ENTREGA) -> {
                    try {
                        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value))
                            throw DateTimeParseException("Formato ISO requerido", value, 0)
                        dates[field] = LocalDate.parse(value)
                    } catch (_: DateTimeParseException) { put(field, FieldIssue.FECHA_ISO) }
                }
                field == PedidoField.VALOR || field == PedidoField.PROPINA -> {
                    val amount = if (Regex("-?[0-9]+(\\.[0-9]+)?").matches(value)) value.toBigDecimalOrNull() else null
                    val issue = when {
                        amount == null -> FieldIssue.DECIMAL
                        // Como en NUMERIC(12,2), se admiten ceros finales sin redondear el importe.
                        amount.stripTrailingZeros().scale() > 2 -> FieldIssue.MAXIMO_DECIMALES
                        amount.abs() >= BigDecimal("10000000000") -> FieldIssue.MAXIMO_ENTEROS
                        field == PedidoField.VALOR && amount.signum() <= 0 -> FieldIssue.VALOR_POSITIVO
                        field == PedidoField.PROPINA && amount.signum() < 0 -> FieldIssue.PROPINA_NO_NEGATIVA
                        else -> null
                    }
                    if (issue != null) put(field, issue)
                }
            }
        }
        val fecha = dates[PedidoField.FECHA]
        val envio = dates[PedidoField.FECHA_ENVIO]
        val entrega = dates[PedidoField.FECHA_ENTREGA]
        if (fecha != null && envio != null && envio.isBefore(fecha)) put(PedidoField.FECHA_ENVIO, FieldIssue.ENVIO_ANTERIOR)
        if (envio != null && entrega != null && entrega.isBefore(envio)) put(PedidoField.FECHA_ENTREGA, FieldIssue.ENTREGA_ANTERIOR)
    }
    // Los textos libres se recortan: los reportes filtran por igualdad exacta y un espacio final impediría encontrar el pedido.
    // El estado no se recorta porque debe coincidir literalmente con el catálogo o con el valor original del servidor.
    fun datos(): DatosPedido = DatosPedido(LocalDate.parse(this[PedidoField.FECHA]), LocalDate.parse(this[PedidoField.FECHA_ENVIO]),
        LocalDate.parse(this[PedidoField.FECHA_ENTREGA]), texto(PedidoField.CLIENTE), texto(PedidoField.PROVEEDOR),
        BigDecimal(this[PedidoField.VALOR]), this[PedidoField.ESTADO], texto(PedidoField.PAIS), texto(PedidoField.DEPARTAMENTO),
        texto(PedidoField.CIUDAD), texto(PedidoField.NOMENCLATURA), BigDecimal(this[PedidoField.PROPINA]))
    private fun texto(field: PedidoField) = this[field].trim()
    companion object {
        fun from(p: Pedido) = PedidoDraft(mapOf(PedidoField.FECHA to p.fecha.toString(), PedidoField.FECHA_ENVIO to p.fechaEnvio.toString(),
            PedidoField.FECHA_ENTREGA to p.fechaEntrega.toString(), PedidoField.CLIENTE to p.cliente, PedidoField.PROVEEDOR to p.proveedor,
            PedidoField.VALOR to p.valor.toPlainString(), PedidoField.ESTADO to p.estado, PedidoField.PAIS to p.pais,
            PedidoField.DEPARTAMENTO to p.departamento, PedidoField.CIUDAD to p.ciudad,
            PedidoField.NOMENCLATURA to p.nomenclaturaVivienda, PedidoField.PROPINA to p.propina.toPlainString()), estadoOriginal = p.estado)
    }
}
/** Estado observable de pedidos: vista, datos, borrador, ocupación, errores de validación y resultado de la última escritura. */
data class PedidosUiState(
    val page: PedidosPage = PedidosPage.LISTA,
    val items: List<Pedido> = emptyList(), val selected: Pedido? = null,
    val draft: PedidoDraft = PedidoDraft(), val editingId: Long? = null,
    val dirty: Boolean = false, val busy: Boolean = false,
    val needsReconciliation: Boolean = false,
    /** Verdadero cuando la lista ya se consultó con éxito al menos una vez (distingue «cargando» y «falló» de «vacía»). */
    val cargado: Boolean = false,
    val validationAttempt: Int = 0,
    val error: AppError? = null, val writeFailure: Boolean = false,
    val issues: Map<PedidoField, FieldIssue> = emptyMap(), val confirmDelete: Boolean = false,
    /** Resultado correcto de la última escritura; se limpia al iniciar cualquier otra operación. */
    val exito: ExitoPedido? = null
)

/** Resultado correcto de la última escritura, mostrado como confirmación. */
enum class ExitoPedido { CREADO, ACTUALIZADO, ELIMINADO }

/** Gestiona el CRUD de pedidos: carga, borrador, validación, guardado sin envíos repetidos y eliminación confirmada. */
class PedidosViewModel(private val repository: PedidosRepository, testScope: CoroutineScope? = null) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val state = MutableStateFlow(PedidosUiState())
    val uiState = state.asStateFlow()
    init { cargar() }
    private fun execute(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null, writeFailure = false, confirmDelete = false, exito = null) }
        scope.launch { try { action() } finally { state.update { it.copy(busy = false) } } }
    }
    private fun failure(error: AppError, write: Boolean = false) {
        val uncertain = write && when (error) {
            AppError.ErrorConexion, AppError.RespuestaInvalida, AppError.ServicioNoDisponible -> true
            is AppError.ErrorHttp -> error.codigo == 408 || error.codigo in 500..599
            else -> false
        }
        state.update { it.copy(error = error, writeFailure = write, needsReconciliation = it.needsReconciliation || uncertain) }
    }
    fun cargar() = execute {
        when (val result = repository.listar()) {
            is AppResult.Fallo -> failure(result.error)
            is AppResult.Exito -> state.update { it.copy(items = result.valor, page = PedidosPage.LISTA, needsReconciliation = false, cargado = true) }
        }
    }
    fun nuevo() {
        if (state.value.busy) return
        state.update { it.copy(page = PedidosPage.EDITOR, draft = PedidoDraft(), selected = null, editingId = null,
            dirty = false, issues = emptyMap(), error = null, writeFailure = false, exito = null) }
    }
    fun continuar() { if (!state.value.busy) state.update { it.copy(page = PedidosPage.EDITOR, error = null, exito = null) } }
    fun cambiar(field: PedidoField, value: String) {
        if (!state.value.busy) state.update {
            val draft = it.draft.with(field, value)
            it.copy(draft = draft, dirty = true, issues = if (it.issues.isEmpty()) emptyMap() else draft.issues())
        }
    }
    fun consultar(id: Long) = execute {
        when (val result = repository.consultar(id)) {
            is AppResult.Fallo -> failure(result.error)
            is AppResult.Exito -> state.update { it.copy(selected = result.valor, page = PedidosPage.DETALLE) }
        }
    }
    fun editar() {
        if (state.value.busy) return
        val current = state.value.selected ?: return
        state.update { it.copy(page = PedidosPage.EDITOR, draft = PedidoDraft.from(current), editingId = current.id,
            dirty = false, issues = emptyMap(), error = null, writeFailure = false, exito = null) }
    }
    fun guardar() {
        if (state.value.busy || state.value.needsReconciliation) return
        val draft = state.value.draft
        val issues = draft.issues()
        if (issues.isNotEmpty()) { state.update { it.copy(issues = issues, validationAttempt = it.validationAttempt + 1) }; return }
        val id = state.value.editingId
        execute {
            val result = if (id == null) repository.crear(draft.datos()) else repository.actualizar(id, draft.datos())
            when (result) {
                is AppResult.Fallo -> failure(result.error, write = true)
                is AppResult.Exito -> state.update { it.copy(selected = result.valor, page = PedidosPage.DETALLE,
                    items = (it.items.filterNot { item -> item.id == result.valor.id } + result.valor).sortedBy { item -> item.id },
                    dirty = false, draft = PedidoDraft(), editingId = null,
                    exito = if (id == null) ExitoPedido.CREADO else ExitoPedido.ACTUALIZADO) }
            }
        }
    }
    fun pedirEliminar() { if (!state.value.busy && !state.value.needsReconciliation && state.value.selected != null) state.update { it.copy(confirmDelete = true) } }
    fun cancelarEliminar() { state.update { it.copy(confirmDelete = false) } }
    fun eliminar() {
        if (!state.value.confirmDelete || state.value.needsReconciliation) return
        val id = state.value.selected?.id ?: return
        execute {
            when (val result = repository.eliminar(id)) {
                is AppResult.Fallo -> failure(result.error, write = true)
                is AppResult.Exito -> state.update { it.copy(page = PedidosPage.LISTA, selected = null,
                    items = it.items.filterNot { item -> item.id == id }, exito = ExitoPedido.ELIMINADO) }
            }
        }
    }
}
