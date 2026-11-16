package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.FlowPreview

/**
 * Contenedor para un campo de texto del formulario. Cuando el usuario toca el campo y este gana el foco, el contenedor
 * completo —etiqueta, entrada y mensaje de error— se desplaza a la vista.
 *
 * - Solo se activa por un toque en el campo: si el foco vuelve al campo por otra causa (por ejemplo, al cerrar un
 *   selector de fecha) la lista no se desplaza sola.
 * - Si el teclado se está abriendo en ese momento, el área visible cambia con `imePadding()`: se repite una vez la
 *   petición cuando el teclado deja de moverse. Si ya estaba abierto no se repite, para no devolver la lista al
 *   campo cuando el usuario se desplaza a mano.
 */
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
internal fun CampoVisible(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val solicitante = remember { BringIntoViewRequester() }
    var tocado by remember { mutableStateOf(false) }
    var solicitudes by remember { mutableIntStateOf(0) }
    val densidad = LocalDensity.current
    val teclado = WindowInsets.ime
    LaunchedEffect(solicitudes) {
        if (solicitudes == 0) return@LaunchedEffect
        solicitante.bringIntoView()
    }
    Column(
        modifier.fillMaxWidth()
            .bringIntoViewRequester(solicitante)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val evento = awaitPointerEvent(PointerEventPass.Initial)
                        if (evento.type == PointerEventType.Press) tocado = true
                    }
                }
            }
            .onFocusChanged { estado ->
                if (estado.hasFocus && tocado) solicitudes++
                if (!estado.hasFocus) tocado = false
            }
    ) { content() }
}
