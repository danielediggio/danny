package org.diggio.obdiggio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* ------------------------------------------------------------------ *
 *  Sfondo in fibra di carbonio (procedurale, leggero).
 * ------------------------------------------------------------------ */
fun DrawScope.carbonBackground() {
    drawRect(Brush.radialGradient(
        colors = listOf(Color(0xFF14161C), Color(0xFF090A0E)),
        center = Offset(size.width / 2f, size.height * 0.32f),
        radius = size.maxDimension * 0.8f,
    ))
    // Trama a incrocio diagonale (twill) a bassissima opacità.
    val step = 16f
    val hi = Color(0x11FFFFFF)
    val lo = Color(0x14000000)
    var i = -size.height
    while (i < size.width) {
        drawLine(hi, Offset(i, 0f), Offset(i + size.height, size.height), 2f)
        drawLine(lo, Offset(i + 4f, 0f), Offset(i + 4f + size.height, size.height), 1f)
        i += step
    }
    i = -size.height
    while (i < size.width) {
        drawLine(lo, Offset(i, size.height), Offset(i + size.height, 0f), 1f)
        i += step
    }
}

/* ------------------------------------------------------------------ *
 *  Display a 7 segmenti (stile LCD).
 * ------------------------------------------------------------------ */
private val SEG = mapOf(
    '0' to booleanArrayOf(true, true, true, true, true, true, false),
    '1' to booleanArrayOf(false, true, true, false, false, false, false),
    '2' to booleanArrayOf(true, true, false, true, true, false, true),
    '3' to booleanArrayOf(true, true, true, true, false, false, true),
    '4' to booleanArrayOf(false, true, true, false, false, true, true),
    '5' to booleanArrayOf(true, false, true, true, false, true, true),
    '6' to booleanArrayOf(true, false, true, true, true, true, true),
    '7' to booleanArrayOf(true, true, true, false, false, false, false),
    '8' to booleanArrayOf(true, true, true, true, true, true, true),
    '9' to booleanArrayOf(true, true, true, true, false, true, true),
    '-' to booleanArrayOf(false, false, false, false, false, false, true),
    ' ' to booleanArrayOf(false, false, false, false, false, false, false),
)

/** Disegna una cifra a 7 segmenti nel box (x0,y0,w,h). Ordine: a,b,c,d,e,f,g. */
private fun DrawScope.drawDigit(x0: Float, y0: Float, w: Float, h: Float, ch: Char, color: Color) {
    val segs = SEG[ch] ?: SEG[' ']!!
    val t = w * 0.17f
    val halfH = (h - 3f * t) / 2f
    val on = color
    val off = color.copy(alpha = 0.07f)
    fun bar(x: Float, y: Float, bw: Float, bh: Float, lit: Boolean) {
        val c = if (lit) on else off
        if (lit) drawRoundRect(c.copy(alpha = 0.25f), Offset(x - 2f, y - 2f), Size(bw + 4f, bh + 4f),
            androidx.compose.ui.geometry.CornerRadius(t * 0.4f))
        drawRoundRect(c, Offset(x, y), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(t * 0.4f))
    }
    bar(x0 + t, y0, w - 2 * t, t, segs[0])                        // a
    bar(x0 + w - t, y0 + t, t, halfH, segs[1])                    // b
    bar(x0 + w - t, y0 + 2 * t + halfH, t, halfH, segs[2])        // c
    bar(x0 + t, y0 + h - t, w - 2 * t, t, segs[3])                // d
    bar(x0, y0 + 2 * t + halfH, t, halfH, segs[4])               // e
    bar(x0, y0 + t, t, halfH, segs[5])                            // f
    bar(x0 + t, y0 + t + halfH, w - 2 * t, t, segs[6])           // g
}

/** Stringa a 7 segmenti centrata, con altezza data. */
@Composable
fun SevenSegment(text: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val h = size.height
        val w = h * 0.58f
        val gap = w * 0.28f
        val totalW = text.length * w + (text.length - 1) * gap
        var x = (size.width - totalW) / 2f
        for (ch in text) {
            drawDigit(x, 0f, w, h, ch, color)
            x += w + gap
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Tachimetro realistico.
 * ------------------------------------------------------------------ */
@Composable
fun Tachometer(
    rpm: Double?,
    maxRpm: Double = 7000.0,
    modifier: Modifier = Modifier,
) {
    val frac = ((rpm ?: 0.0) / maxRpm).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(frac, tween(280), label = "tach")
    val start = 135f
    val total = 270f

    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f

            // Ghiera metallica.
            drawCircle(Brush.sweepGradient(
                listOf(Color(0xFF2A2E36), Color(0xFF8B939E), Color(0xFF20242B),
                    Color(0xFF6B7078), Color(0xFF2A2E36)), c), r, center = c)
            drawCircle(Color(0xFF0A0B0F), r * 0.9f, c)
            // Zone colorate (verde bassa, rossa redline).
            fun zone(from: Float, to: Float, col: Color) = drawArc(
                col, start + total * from, total * (to - from), false,
                topLeft = Offset(c.x - r * 0.82f, c.y - r * 0.82f),
                size = Size(r * 1.64f, r * 1.64f), style = Stroke(r * 0.05f))
            zone(0f, 0.30f, Color(0xFF39FF14).copy(alpha = 0.85f))
            zone(0.78f, 1f, Color(0xFFFF3B4E))

            // Tacche + numeri (0..7).
            val majors = 7
            for (i in 0..majors) {
                val f = i / majors.toFloat()
                val a = Math.toRadians((start + total * f).toDouble())
                val ro = r * 0.86f
                val ri = r * 0.74f
                val col = when { f < 0.30f -> Color(0xFF39FF14); f > 0.78f -> Color(0xFFFF3B4E); else -> Color.White }
                drawLine(col, Offset(c.x + ro * cos(a).toFloat(), c.y + ro * sin(a).toFloat()),
                    Offset(c.x + ri * cos(a).toFloat(), c.y + ri * sin(a).toFloat()), 6f)
                // Tacche minori
                if (i < majors) for (m in 1..3) {
                    val fm = (i + m / 4f) / majors.toFloat()
                    val am = Math.toRadians((start + total * fm).toDouble())
                    drawLine(Color(0x66FFFFFF),
                        Offset(c.x + r * 0.84f * cos(am).toFloat(), c.y + r * 0.84f * sin(am).toFloat()),
                        Offset(c.x + r * 0.78f * cos(am).toFloat(), c.y + r * 0.78f * sin(am).toFloat()), 3f)
                }
            }

            // Lancetta con bagliore.
            val na = Math.toRadians((start + total * animated).toDouble())
            val tip = Offset(c.x + r * 0.82f * cos(na).toFloat(), c.y + r * 0.82f * sin(na).toFloat())
            val tail = Offset(c.x - r * 0.14f * cos(na).toFloat(), c.y - r * 0.14f * sin(na).toFloat())
            listOf(18f to 0.18f, 11f to 0.30f, 6f to 1f).forEach { (wd, al) ->
                drawLine(Color(0xFF39FF14).copy(alpha = al), tail, tip, wd, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            // Hub centrale.
            drawCircle(Color(0xFF15171D), r * 0.14f, c)
            drawCircle(Color(0xFF39FF14), r * 0.05f, c)
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Icone per le tile (disegnate a mano).
 * ------------------------------------------------------------------ */
enum class TileIcon { TEMP, TURBO, LOAD, VOLT }

fun DrawScope.drawTileIcon(kind: TileIcon, center: Offset, s: Float, color: Color) {
    val stroke = Stroke(s * 0.10f)
    when (kind) {
        TileIcon.TEMP -> {
            val stemW = s * 0.22f
            drawRoundRect(color, Offset(center.x - stemW / 2, center.y - s * 0.45f),
                Size(stemW, s * 0.7f), androidx.compose.ui.geometry.CornerRadius(stemW / 2),
                style = stroke)
            drawCircle(color, s * 0.22f, Offset(center.x, center.y + s * 0.35f))
        }
        TileIcon.TURBO -> {
            drawCircle(color, s * 0.42f, center, style = stroke)
            for (k in 0 until 7) {
                val a = Math.toRadians((k * 360.0 / 7))
                drawLine(color, center,
                    Offset(center.x + s * 0.34f * cos(a).toFloat(), center.y + s * 0.34f * sin(a).toFloat()),
                    s * 0.06f)
            }
            drawCircle(color, s * 0.10f, center)
        }
        TileIcon.LOAD -> {
            drawArc(color, 150f, 240f, false,
                topLeft = Offset(center.x - s * 0.42f, center.y - s * 0.42f),
                size = Size(s * 0.84f, s * 0.84f), style = stroke)
            val a = Math.toRadians(150.0 + 240.0 * 0.62)
            drawLine(color, center,
                Offset(center.x + s * 0.34f * cos(a).toFloat(), center.y + s * 0.34f * sin(a).toFloat()),
                s * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        TileIcon.VOLT -> {
            drawRoundRect(color, Offset(center.x - s * 0.42f, center.y - s * 0.28f),
                Size(s * 0.84f, s * 0.56f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
                style = stroke)
            drawRect(color, Offset(center.x - s * 0.26f, center.y - s * 0.4f), Size(s * 0.14f, s * 0.12f))
            drawRect(color, Offset(center.x + s * 0.12f, center.y - s * 0.4f), Size(s * 0.14f, s * 0.12f))
            // + e -
            drawLine(color, Offset(center.x - s * 0.24f, center.y), Offset(center.x - s * 0.08f, center.y), s * 0.06f)
            drawLine(color, Offset(center.x - s * 0.16f, center.y - s * 0.08f), Offset(center.x - s * 0.16f, center.y + s * 0.08f), s * 0.06f)
            drawLine(color, Offset(center.x + s * 0.08f, center.y), Offset(center.x + s * 0.24f, center.y), s * 0.06f)
        }
    }
}
