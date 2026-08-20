package org.diggio.obdiggio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF21B0F0)
private val Ok = Color(0xFF34BF66)
private val Warn = Color(0xFFE6594D)

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = Ok,
    error = Warn,
    background = Color(0xFF121316),
    surface = Color(0xFF1E2126),
    onPrimary = Color(0xFF001019),
    onBackground = Color(0xFFEBEDF0),
    onSurface = Color(0xFFEBEDF0),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = Ok,
    error = Warn,
)

@Composable
fun ObdiggioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
