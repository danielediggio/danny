package org.diggio.obdiggio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.min

/** Lancetta radiale neon con bagliore, stile cruscotto tuner. */
@Composable
fun NeonGauge(
    label: String,
    value: Double?,
    max: Double,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val frac = ((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = frac, animationSpec = tween(350), label = "gauge")

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = size.minDimension * 0.085f
            val d = size.minDimension - stroke * 1.4f
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            val start = 135f
            val total = 270f

            // Traccia di fondo.
            drawArc(
                color = Color(0xFF1B2438), startAngle = start, sweepAngle = total,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Bagliore: più passate larghe e trasparenti + tratto pieno.
            val sweep = total * animated
            listOf(stroke * 2.6f to 0.10f, stroke * 1.8f to 0.18f, stroke to 1f).forEach { (w, a) ->
                drawArc(
                    color = color.copy(alpha = a), startAngle = start, sweepAngle = sweep,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), color = Neon.Muted, fontSize = 11.sp,
                letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "—",
                color = Neon.Text, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
            )
            Text(unit.uppercase(), color = color, fontSize = 11.sp, letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

/** Barra orizzontale neon (es. sovralimentazione turbo). */
@Composable
fun NeonBar(
    label: String,
    value: Double?,
    max: Double,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val frac = ((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = frac, animationSpec = tween(300), label = "bar")
    Column(modifier) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(label.uppercase(), color = Neon.Muted, fontSize = 11.sp, letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold)
            Text(
                text = (value?.let { "%.2f".format(it) } ?: "—") + "  " + unit,
                color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 4.dp)) {
            val h = size.height
            val r = h / 2f
            drawRoundRect(
                color = Color(0xFF1B2438), size = Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            val w = size.width * animated
            if (w > 0f) {
                listOf(h * 1.0f to 0.20f, h * 0.7f to 1f).forEach { (_, a) ->
                    drawRoundRect(
                        color = color.copy(alpha = a),
                        size = Size(min(w, size.width), h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                }
            }
        }
    }
}
