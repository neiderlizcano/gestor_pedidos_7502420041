package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neiderlizcano.gestorpedidos.nativo.R
import java.time.LocalDate

/** Fondo blanco de los campos, como en la maqueta: se distinguen del gris claro de la pantalla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun colorCampo() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PedidoDateField(value: String, title: Int, tag: String, enabled: Boolean, error: Int?, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, enabled = enabled, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().testTag(tag)) {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
                Text(value.ifEmpty { stringResource(R.string.pedido_choose_date) })
            }
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(tag.replace("pedidoField_", "pedidoError_"))) }
    }
    if (open) {
        val initial = remember { runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }.pickerMillis() }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial, yearRange = 0..9999)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, modifier = Modifier.testTag("pedidoDateConfirm"),
                onClick = { picker.selectedDateMillis?.let { onChange(pickerDate(it).toString()) }; open = false }) { Text(stringResource(R.string.pedido_date_confirm)) } },
            dismissButton = { TextButton(onClick = { open = false }, modifier = Modifier.testTag("pedidoDateCancel")) { Text(stringResource(R.string.pedidos_cancel)) } }) {
            TextButton(onClick = { picker.displayMode = if (picker.displayMode == DisplayMode.Picker) DisplayMode.Input else DisplayMode.Picker },
                modifier = Modifier.testTag("pedidoDateMode")) {
                Text(stringResource(if (picker.displayMode == DisplayMode.Picker) R.string.pedido_date_input else R.string.pedido_date_calendar))
            }
            DatePicker(state = picker, showModeToggle = false, modifier = Modifier.testTag("pedidoDatePicker"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PedidoEstadoField(value: String, enabled: Boolean, error: Int?, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Lista cerrada: no se escribe a mano, por lo que el teclado no aparece ni tapa el campo.
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value = value.ifEmpty { ESTADO_PLACEHOLDER }, onValueChange = {}, readOnly = true, singleLine = true, enabled = enabled,
            label = { Text(stringResource(R.string.pedido_estado)) }, isError = error != null, colors = colorCampo(),
            // El error queda justo debajo del campo; sin error se muestra una ayuda corta.
            supportingText = { Text(stringResource(error ?: R.string.pedido_estado_help), modifier = if (error != null) Modifier.testTag("pedidoError_ESTADO") else Modifier) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth().testTag("pedidoField_ESTADO"))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.testTag("pedidoEstadoMenu")) {
            OPCIONES_ESTADO.forEach { opcion ->
                // «Seleccione un estado» limpia la elección y sigue siendo inválido para guardar.
                DropdownMenuItem(text = { Text(opcion) }, onClick = { onChange(if (opcion == ESTADO_PLACEHOLDER) "" else opcion); expanded = false })
            }
        }
    }
}
