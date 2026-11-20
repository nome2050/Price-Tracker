package com.pricetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pricetracker.app.ui.theme.PT
import java.util.Locale
import kotlin.math.abs

@Composable
fun SparkLine(values: List<Double>, modifier: Modifier = Modifier) {
    val color = when {
        values.size < 2 -> PT.Blue
        values.last() <= values.first() -> PT.Down
        else -> PT.Up
    }
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(color.copy(alpha = .5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f, StrokeCap.Round)
            return@Canvas
        }
        val min = values.min(); val max = values.max(); val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = (size.height * (1 - (v - min) / span)).toFloat().coerceIn(4f, size.height - 4f)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

@Composable
fun ChangeChip(from: Double?, to: Double?) {
    if (from == null || to == null || from == 0.0) return
    val pct = (to - from) / from * 100
    if (abs(pct) < 0.05) return
    val good = pct < 0
    val c = if (good) PT.Down else PT.Up
    Text(
        (if (pct > 0) "▲ " else "▼ ") + String.format(Locale.getDefault(), "%.1f%%", abs(pct)),
        color = c, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.background(c.copy(alpha = .14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
