package org.diggio.obdiggio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonGreen = Color(0xFF8CFF00)
val NeonCyan = Color(0xFF00E5FF)
val NeonPink = Color(0xFFFF245F)
val PanelBlack = Color(0xFF080B0D)
val PanelDark = Color(0xFF11171A)
val Steel = Color(0xFFAEB7BD)

private val ObdiggioDarkColors = darkColorScheme(
    primary = NeonGreen,
    secondary = NeonCyan,
    tertiary = NeonPink,
    error = NeonPink,
    background = Color.Black,
    surface = PanelBlack,
    surfaceVariant = PanelDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF1F5F7),
    onSurface = Color(0xFFF1F5F7),
    outline = Color(0xFF435159),
)

@Composable
fun ObdiggioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ObdiggioDarkColors, content = content)
}

