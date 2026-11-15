package com.neiderlizcano.gestorpedidos.nativo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    primaryContainer = Color(0xFF9EF2E1),
    background = Color(0xFFF4FBF7)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5C5),
    primaryContainer = Color(0xFF005047),
    background = Color(0xFF101D19),
    onBackground = Color(0xFFE0EDE6),
    surface = Color(0xFF101D19),
    onSurface = Color(0xFFE0EDE6)
)

@Composable
fun GestorPedidosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
