package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PaginaReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PedidoReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PerfilReporte

// Componentes compartidos por las cuatro pantallas. No contienen lógica de negocio ni acceso a red.

/**
 * Estructura común: encabezado, filtros, acciones, estado (carga, error, vacío) y resultados.
 * [filas] añade las filas del resultado y solo se invoca cuando hay datos no vacíos.
 */
@Composable
internal fun <Form, D> ReporteLayout(
    titulo: Int,
    ui: ReporteUi<Form, D>,
    onConsultar: () -> Unit,
    onLimpiar: () -> Unit,
    onReintentar: () -> Unit,
    vacio: (D) -> Boolean,
    filtros: @Composable () -> Unit,
    filas: LazyListScope.(D) -> Unit,
    pie: (@Composable (D) -> Unit)? = null
) {
    LazyColumn(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp).testTag("reporteLista"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { Text(stringResource(titulo), style = MaterialTheme.typography.titleLarge) }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { filtros() } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConsultar, enabled = !ui.busy, modifier = Modifier.testTag("reporteConsultar")) {
                    Text(stringResource(R.string.reportes_search))
                }
                OutlinedButton(onClick = onLimpiar, enabled = !ui.busy, modifier = Modifier.testTag("reporteLimpiar")) {
                    Text(stringResource(R.string.reportes_clear))
                }
            }
        }
        if (ui.busy) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("reporteProgreso"))
                Text(stringResource(R.string.reportes_loading))
            }
        }
        ui.error?.let { error ->
            item {
                Column {
                    Text(stringResource(mensajeError(error)), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("reporteError"))
                    TextButton(onClick = onReintentar, enabled = !ui.busy, modifier = Modifier.testTag("reporteReintentar")) {
                        Text(stringResource(R.string.reportes_retry))
                    }
                }
            }
        }
        val datos = ui.datos
        if (datos != null) {
            if (vacio(datos)) {
                item { Text(stringResource(R.string.reportes_empty), modifier = Modifier.testTag("reporteVacio")) }
            } else {
                filas(datos)
            }
            if (pie != null) item { pie(datos) }
        } else if (!ui.busy && ui.error == null) {
            item { Text(stringResource(R.string.reportes_initial_hint), modifier = Modifier.testTag("reporteInicial")) }
        }
    }
}

/** Campo de texto con mensaje de ayuda cuando existe un problema de validación. */
@Composable
internal fun CampoFiltro(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: Int,
    etiquetaTag: String,
    habilitado: Boolean,
    problema: ReporteProblema?,
    tipoTeclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, label = { Text(stringResource(etiqueta)) },
        singleLine = true, enabled = habilitado, isError = problema != null,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
        supportingText = { problema?.let { Text(stringResource(mensajeProblema(it))) } },
        modifier = Modifier.fillMaxWidth().testTag(etiquetaTag)
    )
}

/** Fila de chips de selección única. */
@Composable
internal fun <T> OpcionesFila(
    titulo: Int,
    opciones: List<T>,
    seleccionada: T,
    habilitado: Boolean,
    etiqueta: @Composable (T) -> String,
    tagPrefijo: String,
    onSeleccion: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(titulo), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.forEach { opcion ->
                FilterChip(
                    selected = opcion == seleccionada, onClick = { onSeleccion(opcion) }, enabled = habilitado,
                    label = { Text(etiqueta(opcion)) }, modifier = Modifier.testTag(tagPrefijo + opcion.toString())
                )
            }
        }
    }
}

/** Controles de paginación; solo se muestran para los reportes paginados. */
@Composable
internal fun Paginador(pagina: PaginaReporte<*>, ocupado: Boolean, onAnterior: () -> Unit, onSiguiente: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onAnterior, enabled = !ocupado && pagina.pagina > 0, modifier = Modifier.testTag("reporteAnterior")) {
            Text(stringResource(R.string.reportes_previous))
        }
        Text(stringResource(R.string.reportes_page, pagina.pagina + 1), modifier = Modifier.padding(top = 10.dp).testTag("reportePagina"))
        OutlinedButton(onClick = onSiguiente, enabled = !ocupado && pagina.haySiguiente, modifier = Modifier.testTag("reporteSiguiente")) {
            Text(stringResource(R.string.reportes_next))
        }
    }
}

@Composable
internal fun PedidoReporteCard(pedido: PedidoReporte) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("reportePedido_" + pedido.id)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reportes_pedido_title, pedido.id.toString()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reportes_pedido_parties, pedido.cliente, pedido.proveedor))
            Text(stringResource(R.string.reportes_pedido_amounts, pedido.estado, pedido.valor.toPlainString(), pedido.propina.toPlainString()))
            Text(stringResource(R.string.reportes_pedido_dates, pedido.fecha.toString(), pedido.fechaEnvio.toString(), pedido.fechaEntrega.toString()))
            Text(stringResource(R.string.reportes_pedido_place, pedido.pais, pedido.departamento, pedido.ciudad, pedido.nomenclaturaVivienda))
        }
    }
}

@Composable
internal fun PerfilReporteCard(perfil: PerfilReporte) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("reportePerfil_" + perfil.username)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(perfil.nombre, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reportes_perfil_username, perfil.username))
            Text(perfil.email)
        }
    }
}

internal fun mensajeProblema(problema: ReporteProblema): Int = when (problema) {
    ReporteProblema.FECHA_ISO -> R.string.reportes_problem_fecha
    ReporteProblema.RANGO_FECHAS -> R.string.reportes_problem_rango
    ReporteProblema.FILTRO_REQUERIDO -> R.string.reportes_problem_ubicacion
    ReporteProblema.OBLIGATORIO -> R.string.reportes_problem_obligatorio
    ReporteProblema.DEMASIADO_LARGO -> R.string.reportes_problem_largo
    ReporteProblema.CORREO_INVALIDO -> R.string.reportes_problem_correo
}

internal fun mensajeError(error: AppError): Int = when (error) {
    AppError.SolicitudInvalida -> R.string.reportes_error_invalid
    AppError.SesionNoValida -> R.string.reportes_error_session
    AppError.AccesoDenegado -> R.string.reportes_error_forbidden
    AppError.ServicioNoDisponible -> R.string.reportes_error_unavailable
    AppError.ErrorConexion -> R.string.reportes_error_connection
    AppError.RespuestaInvalida -> R.string.reportes_error_response
    is AppError.ErrorHttp -> R.string.reportes_error_unknown
}
