package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.PedidosRepository
import com.neiderlizcano.gestorpedidos.nativo.ubicacion.UsarMiUbicacion

@Composable
fun PedidosRoute(repository: PedidosRepository) {
    val factory = remember(repository) { object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == PedidosViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return PedidosViewModel(repository) as T
        }
    } }
    val vm: PedidosViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    PedidosScreen(state, vm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PedidosScreen(state: PedidosUiState, vm: PedidosViewModel) {
    BackHandler(enabled = state.page != PedidosPage.LISTA) { if (!state.busy) vm.cargar() }
    // Cada destino conserva su posición al rotar y empieza sin el desplazamiento de la vista anterior.
    key(state.page, state.editingId) {
    val scroll = rememberLazyListState()
    // Al guardar se cierra el teclado para que los errores del formulario queden a la vista.
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.validationAttempt) {
        if (state.page == PedidosPage.EDITOR && state.issues.isNotEmpty()) {
            val first = PedidoField.entries.indexOfFirst { it in state.issues }
            val prefix = 2 + (if (state.busy) 1 else 0) + (if (state.error != null) 1 else 0) + (if (state.needsReconciliation) 1 else 0)
            scroll.animateScrollToItem(prefix + first)
        }
    }
    Row(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
    LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp).testTag("pedidosScreen"), state = scroll,
        verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(stringResource(when (state.page) {
                PedidosPage.LISTA -> R.string.pedidos_title
                PedidosPage.DETALLE -> R.string.pedidos_detail
                PedidosPage.EDITOR -> if (state.editingId == null) R.string.pedidos_new else R.string.pedidos_edit
            }), style = MaterialTheme.typography.titleLarge)
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("pedidosProgress")) }
        state.error?.let { error -> item {
            Text(stringResource(errorMessage(error, state.writeFailure)), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("pedidosError"))
            if (state.writeFailure && !state.needsReconciliation) TextButton(onClick = vm::cargar, enabled = !state.busy) { Text(stringResource(R.string.pedidos_check)) }
        } }
        if (state.needsReconciliation) item {
            Text(stringResource(R.string.pedidos_reconciliation), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::cargar, enabled = !state.busy, modifier = Modifier.testTag("pedidosComprobar")) {
                Text(stringResource(R.string.pedidos_check))
            }
        }
        when (state.page) {
            PedidosPage.LISTA -> {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::nuevo, enabled = !state.busy, modifier = Modifier.testTag("pedidoNuevo")) { Text(stringResource(R.string.pedidos_new)) }
                        TextButton(onClick = vm::cargar, enabled = !state.busy, modifier = Modifier.testTag("pedidosRefresh")) { Text(stringResource(R.string.pedidos_refresh)) }
                    }
                    if (state.dirty) TextButton(onClick = vm::continuar, enabled = !state.busy) { Text(stringResource(R.string.pedidos_resume)) }
                    when {
                        // Primera consulta en curso: no se muestra «vacío» ni una lista a medias.
                        !state.cargado && state.busy -> Text(stringResource(R.string.pedidos_loading), modifier = Modifier.testTag("pedidosCargando"))
                        // La primera consulta falló: se explica y se ofrece reintentar en lugar de una lista vacía engañosa.
                        !state.cargado && state.error != null -> Button(onClick = vm::cargar, enabled = !state.busy, modifier = Modifier.testTag("pedidosReintentar")) {
                            Text(stringResource(R.string.pedidos_retry))
                        }
                        state.cargado && state.items.isEmpty() && !state.busy && state.error == null -> Text(stringResource(R.string.pedidos_empty))
                    }
                }
                items(state.items, key = { it.id }) { pedido ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("pedidoRow_" + pedido.id)
                        .clickable(enabled = !state.busy) { vm.consultar(pedido.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.pedidos_id, pedido.id.toString()), style = MaterialTheme.typography.titleMedium)
                            Text(pedido.cliente); Text(pedido.estado); Text(pedido.valor.toPlainString())
                        }
                    }
                }
            }
            PedidosPage.DETALLE -> {
                state.selected?.let { pedido ->
                    item { Text(stringResource(R.string.pedidos_id, pedido.id.toString()), modifier = Modifier.testTag("pedidoId")) }
                    items(PedidoField.entries) { field ->
                        Column { Text(stringResource(label(field)), style = MaterialTheme.typography.labelLarge)
                            Text(PedidoDraft.from(pedido)[field]) }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::editar, enabled = !state.busy, modifier = Modifier.testTag("pedidoEditar")) { Text(stringResource(R.string.pedidos_edit)) }
                            OutlinedButton(onClick = vm::pedirEliminar, enabled = !state.busy && !state.needsReconciliation, modifier = Modifier.testTag("pedidoEliminar")) { Text(stringResource(R.string.pedidos_delete)) }
                        }
                    }
                }
            }
            PedidosPage.EDITOR -> {
                item { Text(stringResource(R.string.pedidos_id, state.editingId?.toString() ?: stringResource(R.string.pedidos_generated)), modifier = Modifier.testTag("pedidoId")) }
                items(PedidoField.entries, key = { it.name }) { field ->
                    val error = state.issues[field]?.let(::issueMessage)
                    if (field in setOf(PedidoField.FECHA, PedidoField.FECHA_ENVIO, PedidoField.FECHA_ENTREGA)) {
                        PedidoDateField(state.draft[field], label(field), "pedidoField_" + field.name, !state.busy, error) { vm.cambiar(field, it) }
                    } else if (field == PedidoField.ESTADO) {
                        PedidoEstadoField(state.draft[field], !state.busy, error) { vm.cambiar(field, it) }
                    } else {
                    CampoVisible {
                        OutlinedTextField(state.draft[field], { vm.cambiar(field, it) }, label = { Text(stringResource(label(field))) },
                            enabled = !state.busy, singleLine = true, isError = field in state.issues,
                            supportingText = { state.issues[field]?.let { Text(stringResource(issueMessage(it)), modifier = Modifier.testTag("pedidoError_" + field.name)) } }, modifier = Modifier.fillMaxWidth().testTag("pedidoField_" + field.name))
                    }
                    }
                    // «Usar mi ubicación» va debajo de nomenclatura. Solo aplica los campos que el servicio entregó;
                    // los demás conservan lo escrito y los cuatro siguen siendo editables.
                    if (field == PedidoField.NOMENCLATURA) {
                        UsarMiUbicacion(modifier = Modifier.padding(top = 8.dp), onDireccion = { sugerida ->
                            sugerida.pais?.let { vm.cambiar(PedidoField.PAIS, it) }
                            sugerida.departamento?.let { vm.cambiar(PedidoField.DEPARTAMENTO, it) }
                            sugerida.ciudad?.let { vm.cambiar(PedidoField.CIUDAD, it) }
                            sugerida.nomenclatura?.let { vm.cambiar(PedidoField.NOMENCLATURA, it) }
                        })
                    }
                }
                item { Button(onClick = { foco.clearFocus(); teclado?.hide(); vm.guardar() }, enabled = !state.busy && !state.needsReconciliation, modifier = Modifier.fillMaxWidth().testTag("pedidoGuardar")) { Text(stringResource(R.string.pedidos_save)) } }
            }
        }
        if (state.page != PedidosPage.LISTA) item {
            TextButton(onClick = vm::cargar, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("pedidosVolver")) { Text(stringResource(R.string.pedidos_back)) }
        }
    }
    }
    }
    if (state.confirmDelete) AlertDialog(onDismissRequest = vm::cancelarEliminar,
        title = { Text(stringResource(R.string.pedidos_delete)) }, text = { Text(stringResource(R.string.pedidos_delete_question)) },
        confirmButton = { TextButton(onClick = vm::eliminar, modifier = Modifier.testTag("pedidoConfirmarEliminar")) { Text(stringResource(R.string.pedidos_confirm)) } },
        dismissButton = { TextButton(onClick = vm::cancelarEliminar) { Text(stringResource(R.string.pedidos_cancel)) } })
}

private fun label(field: PedidoField): Int = when (field) {
    PedidoField.FECHA -> R.string.pedido_fecha
    PedidoField.FECHA_ENVIO -> R.string.pedido_fecha_envio
    PedidoField.FECHA_ENTREGA -> R.string.pedido_fecha_entrega
    PedidoField.CLIENTE -> R.string.pedido_cliente
    PedidoField.PROVEEDOR -> R.string.pedido_proveedor
    PedidoField.VALOR -> R.string.pedido_valor
    PedidoField.ESTADO -> R.string.pedido_estado
    PedidoField.PAIS -> R.string.pedido_pais
    PedidoField.DEPARTAMENTO -> R.string.pedido_departamento
    PedidoField.CIUDAD -> R.string.pedido_ciudad
    PedidoField.NOMENCLATURA -> R.string.pedido_nomenclatura
    PedidoField.PROPINA -> R.string.pedido_propina
}
private fun errorMessage(error: AppError, write: Boolean): Int = when (error) {
    AppError.SolicitudInvalida -> R.string.pedidos_invalid
    AppError.SesionNoValida -> if (write) R.string.pedidos_session_write else R.string.pedidos_session_read
    AppError.AccesoDenegado -> R.string.pedidos_forbidden
    AppError.ServicioNoDisponible -> R.string.pedidos_unavailable
    AppError.ErrorConexion, AppError.RespuestaInvalida -> if (write) R.string.pedidos_uncertain else R.string.pedidos_connection
    is AppError.ErrorHttp -> R.string.pedidos_unknown
}

private fun issueMessage(issue: FieldIssue): Int = when (issue) {
    FieldIssue.OBLIGATORIO -> R.string.pedidos_required
    FieldIssue.FECHA_ISO -> R.string.pedidos_date_help
    FieldIssue.DECIMAL -> R.string.pedidos_decimal_help
    FieldIssue.VALOR_POSITIVO -> R.string.pedidos_positive_value
    FieldIssue.PROPINA_NO_NEGATIVA -> R.string.pedidos_nonnegative_tip
    FieldIssue.MAXIMO_ENTEROS -> R.string.pedidos_max_integer_digits
    FieldIssue.MAXIMO_DECIMALES -> R.string.pedidos_max_decimal_digits
    FieldIssue.ENVIO_ANTERIOR -> R.string.pedidos_shipping_order
    FieldIssue.ENTREGA_ANTERIOR -> R.string.pedidos_delivery_order
    FieldIssue.ESTADO_INVALIDO -> R.string.pedidos_state_invalid
}
