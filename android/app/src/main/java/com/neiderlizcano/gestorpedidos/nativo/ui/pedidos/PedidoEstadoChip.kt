package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Insignia de color por estado, como en la maqueta: ámbar, azul, índigo, verde y rojo. */
@Composable
internal fun EstadoChip(estado: String, modifier: Modifier = Modifier) {
    val (fondo, texto) = when (estado) {
        "Pendiente" -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        "En preparación" -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        "Enviado" -> Color(0xFFE0E7FF) to Color(0xFF3730A3)
        "Entregado" -> Color(0xFFDCFCE7) to Color(0xFF166534)
        "Cancelado" -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(estado, style = MaterialTheme.typography.labelMedium, color = texto,
        modifier = modifier.background(fondo, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Título de sección de los formularios: mayúsculas pequeñas sobre el grupo de campos. */
@Composable
internal fun SeccionTitulo(texto: String, modifier: Modifier = Modifier) {
    Text(texto.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}
