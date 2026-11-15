package com.neiderlizcano.gestorpedidos.nativo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Jerarquía visual de las acciones: una principal por pantalla, las demás neutras y la destructiva en rojo. */
enum class EstiloBoton { PRIMARIO, SECUNDARIO, PELIGRO }

/**
 * Botón de la aplicación: esquinas de 14 dp, alto mínimo de 52 dp, icono vectorial opcional (sin emoji), reducción
 * elástica al pulsarlo e indicador de progreso que aparece y desaparece con animación mientras la acción está en curso.
 * El indicador es solo visual: quien llama sigue controlando `enabled` para impedir envíos repetidos.
 */
@Composable
fun BotonApp(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloBoton = EstiloBoton.PRIMARIO,
    icono: ImageVector? = null,
    enabled: Boolean = true,
    cargando: Boolean = false
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "escalaBoton")
    val forma = RoundedCornerShape(14.dp)
    val base = modifier.heightIn(min = 52.dp).graphicsLayer { scaleX = escala; scaleY = escala }
    val contenido: @Composable RowScope.() -> Unit = {
        Row(Modifier.animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(visible = cargando, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (icono != null && !cargando) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(texto, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
    when (estilo) {
        EstiloBoton.PRIMARIO -> Button(onClick = onClick, modifier = base, enabled = enabled, shape = forma, interactionSource = interaccion,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp), content = contenido)
        EstiloBoton.SECUNDARIO -> OutlinedButton(onClick = onClick, modifier = base, enabled = enabled, shape = forma, interactionSource = interaccion,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), content = contenido)
        EstiloBoton.PELIGRO -> OutlinedButton(onClick = onClick, modifier = base, enabled = enabled, shape = forma, interactionSource = interaccion,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.38f)), content = contenido)
    }
}
