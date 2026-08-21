package org.diggio.obdiggio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* ------------------------------------------------------------------ *
 *  Sfondo fibra di carbonio + vignettatura.
 * ------------------------------------------------------------------ */
fun DrawScope.carbonBackground() {
    drawRect(Brush.radialGradient(
        colors = listOf(Color(0xFF161922), Color(0xFF0A0C11), Color(0xFF050609)),
        center = Offset(size.width / 2f, size.height * 0.30f),
        radius = size.maxDimension * 0.85f,
    ))
    val step = 15f
    val hi = Color(0x0EFFFFFF)
    val lo = Color(0x18000000)
    var i = -size.height
    while (i < size.width) {
        drawLine(hi, Offset(i, 0f), Offset(i + size.height, size.height), 2f)
        drawLine(lo, Offset(i + 4f, 0f), Offset(i + 4f + size.height, size.height), 1f)
        drawLine(lo, Offset(i, size.height), Offset(i + size.height, 0f), 1f)
        i += step
    }
}

/** Scie di luce che si irradiano dietro al tachimetro (verdi a sx, rosse a dx). */
fun DrawScope.speedStreaks(center: Offset) {
    val rIn = size.minDimension * 0.30f
    val rOut = size.maxDimension * 0.9f
    val n = 46
    for (k in 0 until n) {
        val ang = (k / n.toFloat()) * 2f * Math.PI.toFloat()
        val jitter = ((k * 47) % 13 - 6) * 0.012f
        val a = ang + jitter
        val right = cos(a) > 0
        val col = if (right) Color(0xFFFF2A44) else Color(0xFF19E0C0)
        val len = rIn + (rOut - rIn) * (0.4f + ((k * 31) % 10) / 16f)
        val p1 = Offset(center.x + rIn * cos(a), center.y + rIn * sin(a))
        val p2 = Offset(center.x + len * cos(a), center.y + len * sin(a))
        drawLine(Brush.linearGradient(listOf(col.copy(alpha = 0.22f), Color.Transparent), p1, p2),
            p1, p2, strokeWidth = 2.2f)
    }
}

/* ------------------------------------------------------------------ *
 *  Display a 7 segmenti (LCD).
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

private fun DrawScope.drawDigit(x0: Float, y0: Float, w: Float, h: Float, ch: Char, color: Color) {
    val segs = SEG[ch] ?: SEG[' ']!!
    val t = w * 0.17f
    val halfH = (h - 3f * t) / 2f
    val off = color.copy(alpha = 0.06f)
    fun bar(x: Float, y: Float, bw: Float, bh: Float, lit: Boolean) {
        val c = if (lit) color else off
        if (lit) {
            drawRoundRect(c.copy(alpha = 0.30f), Offset(x - 3f, y - 3f), Size(bw + 6f, bh + 6f),
                androidx.compose.ui.geometry.CornerRadius(t * 0.4f))
        }
        drawRoundRect(c, Offset(x, y), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(t * 0.4f))
    }
    bar(x0 + t, y0, w - 2 * t, t, segs[0])
    bar(x0 + w - t, y0 + t, t, halfH, segs[1])
    bar(x0 + w - t, y0 + 2 * t + halfH, t, halfH, segs[2])
    bar(x0 + t, y0 + h - t, w - 2 * t, t, segs[3])
    bar(x0, y0 + 2 * t + halfH, t, halfH, segs[4])
    bar(x0, y0 + t, t, halfH, segs[5])
    bar(x0 + t, y0 + t + halfH, w - 2 * t, t, segs[6])
}

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
 *  Tachimetro.
 * ------------------------------------------------------------------ */
private val RING_STOPS = listOf(
    Color(0xFF12E36A), Color(0xFF12E36A), Color(0xFF00E5FF),
    Color(0xFFFFE000), Color(0xFFFF7A00), Color(0xFFFF2A44),
)

private fun ringColor(f: Float): Color {
    val x = f.coerceIn(0f, 1f) * (RING_STOPS.size - 1)
    val i = x.toInt().coerceAtMost(RING_STOPS.size - 2)
    return lerp(RING_STOPS[i], RING_STOPS[i + 1], x - i)
}

private fun DrawScope.hexMesh(center: Offset, radius: Float, color: Color) {
    val hr = radius / 7f
    val dx = hr * 1.5f
    val dy = hr * sqrt(3f)
    var row = -7
    while (row <= 7) {
        var col = -7
        while (col <= 7) {
            val cx = center.x + col * dx
            val cy = center.y + row * dy + (if (col % 2 != 0) dy / 2f else 0f)
            val d = sqrt((cx - center.x) * (cx - center.x) + (cy - center.y) * (cy - center.y))
            if (d < radius * 0.92f) {
                val p = Path()
                for (k in 0..6) {
                    val a = Math.toRadians((60.0 * k)).toFloat()
                    val px = cx + hr * 0.92f * cos(a)
                    val py = cy + hr * 0.92f * sin(a)
                    if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                drawPath(p, color, style = Stroke(1f))
            }
            col++
        }
        row++
    }
}

@Composable
fun Tachometer(rpm: Double?, maxRpm: Double = 7000.0, modifier: Modifier = Modifier) {
    val frac = ((rpm ?: 0.0) / maxRpm).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(frac, tween(260), label = "tach")
    val start = 135f
    val total = 270f

    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f

            // Alone colorato (verde -> rosso) dietro alla ghiera.
            val seg = 44
            for (k in 0 until seg) {
                val f = k / (seg - 1f)
                val col = ringColor(f)
                val a0 = start + total * (k / seg.toFloat())
                val sw = total / seg + 1.2f
                listOf(r * 0.24f to 0.05f, r * 0.16f to 0.09f, r * 0.10f to 0.16f).forEach { (wd, al) ->
                    drawArc(col.copy(alpha = al), a0, sw, false,
                        topLeft = Offset(c.x - r * 0.95f, c.y - r * 0.95f),
                        size = Size(r * 1.9f, r * 1.9f), style = Stroke(wd))
                }
            }

            // Ghiera cromata.
            drawCircle(Brush.sweepGradient(
                listOf(Color(0xFF3A3F49), Color(0xFFC7CDD6), Color(0xFF23272E),
                    Color(0xFF9AA0AA), Color(0xFF2C313A), Color(0xFFC7CDD6), Color(0xFF3A3F49)), c),
                r, center = c)
            drawCircle(Color(0xFF1A1D24), r * 0.92f, c)
            // Riflesso in alto.
            drawArc(Color(0x66FFFFFF), 200f, 140f, false,
                topLeft = Offset(c.x - r * 0.97f, c.y - r * 0.97f),
                size = Size(r * 1.94f, r * 1.94f), style = Stroke(r * 0.02f))

            // Quadrante.
            drawCircle(Brush.radialGradient(
                listOf(Color(0xFF10131A), Color(0xFF06070A)), c, r * 0.9f), r * 0.88f, c)

            // Pod centrale a "uovo" con trama esagonale: incornicia il display digitale.
            val podC = Offset(c.x, c.y + r * 0.16f)
            val podHalfW = r * 0.42f
            val podHalfH = r * 0.52f
            drawOval(
                Brush.radialGradient(listOf(Color(0xFF0C1017), Color(0xFF04060A)), podC, r * 0.6f),
                topLeft = Offset(podC.x - podHalfW, podC.y - podHalfH),
                size = Size(podHalfW * 2f, podHalfH * 2f),
            )
            hexMesh(podC, r * 0.38f, Color(0x1400E5FF))
            drawOval(
                Color(0x22FFFFFF),
                topLeft = Offset(podC.x - podHalfW, podC.y - podHalfH),
                size = Size(podHalfW * 2f, podHalfH * 2f),
                style = Stroke(1.5f),
            )

            // Zone colorate sul bordo interno.
            fun zone(from: Float, to: Float, col: Color) = drawArc(
                col, start + total * from, total * (to - from), false,
                topLeft = Offset(c.x - r * 0.80f, c.y - r * 0.80f),
                size = Size(r * 1.6f, r * 1.6f), style = Stroke(r * 0.045f, cap = StrokeCap.Round))
            zone(0f, 0.28f, Color(0xFF12E36A))
            zone(0.80f, 1f, Color(0xFFFF2A44))

            // Tacche maggiori (bianche) + minori + numeri 0-7.
            val majors = 7
            val numPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                color = 0xFFEDF2F8.toInt()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textSize = r * 0.14f
                setShadowLayer(r * 0.03f, 0f, 0f, 0xCC000000.toInt())
            }
            for (i in 0..majors) {
                val f = i / majors.toFloat()
                val a = Math.toRadians((start + total * f).toDouble())
                drawLine(Color(0xFFEDF2F8),
                    Offset(c.x + r * 0.86f * cos(a).toFloat(), c.y + r * 0.86f * sin(a).toFloat()),
                    Offset(c.x + r * 0.71f * cos(a).toFloat(), c.y + r * 0.71f * sin(a).toFloat()), 7f,
                    cap = StrokeCap.Round)
                if (i < majors) for (m in 1..3) {
                    val fm = (i + m / 4f) / majors.toFloat()
                    val am = Math.toRadians((start + total * fm).toDouble())
                    drawLine(Color(0x66FFFFFF),
                        Offset(c.x + r * 0.85f * cos(am).toFloat(), c.y + r * 0.85f * sin(am).toFloat()),
                        Offset(c.x + r * 0.78f * cos(am).toFloat(), c.y + r * 0.78f * sin(am).toFloat()), 3f)
                }
                val lr = r * 0.615f
                val lx = c.x + lr * cos(a).toFloat()
                val ly = c.y + lr * sin(a).toFloat() - (numPaint.ascent() + numPaint.descent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(i.toString(), lx, ly, numPaint)
            }

            // Lancetta verde neon con bagliore + coda.
            val na = Math.toRadians((start + total * animated).toDouble())
            val nc = Color(0xFF76FF3B)
            val tip = Offset(c.x + r * 0.70f * cos(na).toFloat(), c.y + r * 0.70f * sin(na).toFloat())
            val tail = Offset(c.x - r * 0.15f * cos(na).toFloat(), c.y - r * 0.15f * sin(na).toFloat())
            listOf(24f to 0.10f, 15f to 0.20f, 8f to 0.9f, 4f to 1f).forEach { (wd, al) ->
                drawLine(nc.copy(alpha = al), tail, tip, wd, cap = StrokeCap.Round)
            }
            // Hub metallico.
            drawCircle(Brush.radialGradient(listOf(Color(0xFF3B4048), Color(0xFF0E1116)), c, r * 0.17f),
                r * 0.16f, c)
            drawCircle(Color(0xFF20242B), r * 0.11f, c)
            drawCircle(nc.copy(alpha = 0.9f), r * 0.04f, c)
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Icone tile + navigazione (disegnate a mano).
 * ------------------------------------------------------------------ */
enum class TileIcon { TEMP, TURBO, LOAD, VOLT }

fun DrawScope.drawTileIcon(kind: TileIcon, center: Offset, s: Float, color: Color) {
    val stroke = Stroke(s * 0.10f)
    when (kind) {
        TileIcon.TEMP -> {
            val stemW = s * 0.22f
            drawRoundRect(color, Offset(center.x - stemW / 2, center.y - s * 0.45f),
                Size(stemW, s * 0.7f), androidx.compose.ui.geometry.CornerRadius(stemW / 2), style = stroke)
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
                s * 0.08f, cap = StrokeCap.Round)
        }
        TileIcon.VOLT -> {
            drawRoundRect(color, Offset(center.x - s * 0.42f, center.y - s * 0.28f),
                Size(s * 0.84f, s * 0.56f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = stroke)
            drawRect(color, Offset(center.x - s * 0.26f, center.y - s * 0.4f), Size(s * 0.14f, s * 0.12f))
            drawRect(color, Offset(center.x + s * 0.12f, center.y - s * 0.4f), Size(s * 0.14f, s * 0.12f))
            drawLine(color, Offset(center.x - s * 0.24f, center.y), Offset(center.x - s * 0.08f, center.y), s * 0.06f)
            drawLine(color, Offset(center.x - s * 0.16f, center.y - s * 0.08f), Offset(center.x - s * 0.16f, center.y + s * 0.08f), s * 0.06f)
            drawLine(color, Offset(center.x + s * 0.08f, center.y), Offset(center.x + s * 0.24f, center.y), s * 0.06f)
        }
    }
}

enum class NavIcon { GAUGE, ENGINE, SNAPSHOT, LINK }

fun DrawScope.drawNavIcon(kind: NavIcon, center: Offset, s: Float, color: Color) {
    val st = Stroke(s * 0.11f)
    when (kind) {
        NavIcon.GAUGE -> {
            drawArc(color, 145f, 250f, false,
                topLeft = Offset(center.x - s * 0.42f, center.y - s * 0.42f),
                size = Size(s * 0.84f, s * 0.84f), style = st)
            val a = Math.toRadians(250.0)
            drawLine(color, center,
                Offset(center.x + s * 0.32f * cos(a).toFloat(), center.y + s * 0.32f * sin(a).toFloat()),
                s * 0.09f, cap = StrokeCap.Round)
        }
        NavIcon.ENGINE -> {
            // Spia "check engine" stilizzata: blocco con presa e ventola.
            drawRoundRect(color, Offset(center.x - s * 0.4f, center.y - s * 0.2f),
                Size(s * 0.8f, s * 0.45f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = st)
            drawLine(color, Offset(center.x - s * 0.2f, center.y - s * 0.2f),
                Offset(center.x - s * 0.1f, center.y - s * 0.4f), s * 0.11f)
            drawLine(color, Offset(center.x - s * 0.1f, center.y - s * 0.4f),
                Offset(center.x + s * 0.2f, center.y - s * 0.4f), s * 0.11f)
            drawCircle(color, s * 0.06f, Offset(center.x + s * 0.42f, center.y))
        }
        NavIcon.SNAPSHOT -> {
            drawRoundRect(color, Offset(center.x - s * 0.42f, center.y - s * 0.3f),
                Size(s * 0.84f, s * 0.6f), androidx.compose.ui.geometry.CornerRadius(s * 0.1f), style = st)
            drawCircle(color, s * 0.16f, center, style = st)
            drawRect(color, Offset(center.x + s * 0.18f, center.y - s * 0.24f), Size(s * 0.12f, s * 0.06f))
        }
        NavIcon.LINK -> {
            drawArc(color, 40f, 200f, false,
                topLeft = Offset(center.x - s * 0.44f, center.y - s * 0.30f),
                size = Size(s * 0.5f, s * 0.5f), style = st)
            drawArc(color, 220f, 200f, false,
                topLeft = Offset(center.x - s * 0.06f, center.y - s * 0.20f),
                size = Size(s * 0.5f, s * 0.5f), style = st)
        }
    }
}
