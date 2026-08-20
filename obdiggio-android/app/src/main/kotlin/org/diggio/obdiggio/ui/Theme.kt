package org.diggio.obdiggio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette "tuner/underground": nero profondo + neon ciano/magenta. */
object Neon {
    val Bg = Color(0xFF07090F)
    val BgTop = Color(0xFF0C1220)
    val Panel = Color(0xFF121A2B)
    val PanelBorder = Color(0xFF1E2A44)
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFE0209A)
    val Purple = Color(0xFFB026FF)
    val Lime = Color(0xFF39FF14)
    val Red = Color(0xFFFF3B4E)
    val Amber = Color(0xFFFFB020)
    val Text = Color(0xFFEAF4FF)
    val Muted = Color(0xFF7C8BA6)
}

private val DarkColors = darkColorScheme(
    primary = Neon.Cyan,
    secondary = Neon.Magenta,
    tertiary = Neon.Purple,
    error = Neon.Red,
    background = Neon.Bg,
    surface = Neon.Panel,
    onPrimary = Color(0xFF00131A),
    onSecondary = Color(0xFF1A0014),
    onBackground = Neon.Text,
    onSurface = Neon.Text,
)

@Composable
fun ObdiggioTheme(content: @Composable () -> Unit) {
    // Design a tema unico (dark neon), coerente in ogni condizione.
    MaterialTheme(colorScheme = DarkColors, content = content)
}
