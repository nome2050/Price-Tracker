package com.pricetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pricetracker.app.core.Money
import com.pricetracker.app.data.PricePoint
import com.pricetracker.app.ui.theme.PT
import java.text.DateFormat
import java.util.Date

@Composable
fun PriceChart(points: List<PricePoint>, start: Long, end: Long, currency: String?, modifier: Modifier = Modifier) {
    val inRange = points.filter { it.ts in start..end }
    val carry = points.lastOrNull { it.ts < start }
    val series = buildList {
        if (carry != null) add(PricePoint(itemId = carry.itemId, price = carry.price, ts = start))
        addAll(inRange)
        if (isNotEmpty() && last().ts < end) add(PricePoint(itemId = last().itemId, price = last().price, ts = end))
    }
    var sel by remember(series.size, start) { mutableStateOf<Int?>(null) }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(modifier) {
        val shown = sel?.let { series.getOrNull(it) } ?: series.lastOrNull()
        Text(
            if (shown != null) Money.format(shown.price, currency) + "  ·  " + df.format(Date(minOf(shown.ts, System.currentTimeMillis()))) else "—",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(200.dp)
                .pointerInput(series) {
                    detectTapGestures { o -> sel = nearest(series, start, end, o.x, size.width.toFloat()) }
                }
                .pointerInput(series) {
                    detectDragGestures(onDragEnd = { }) { ch, _ -> sel = nearest(series, start, end, ch.position.x, size.width.toFloat()) }
                },
        ) {
            if (series.size < 2) return@Canvas
            val min = series.minOf { it.price }; val max = series.maxOf { it.price }
            val pad = ((max - min) * 0.15).takeIf { it > 1e-9 } ?: (max * 0.05).coerceAtLeast(1.0)
            val lo = min - pad; val hi = max + pad
            fun x(t: Long) = size.width * (t - start).toFloat() / (end - start).coerceAtLeast(1)
            fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
            for (g in 0..3) drawLine(Color(0xFF26262B), Offset(0f, size.height * g / 3), Offset(size.width, size.height * g / 3), 2f)
            val line = Path(); val fill = Path()
            series.forEachIndexed { i, p ->
                val px = x(p.ts); val py = y(p.price)
                if (i == 0) { line.moveTo(px, py); fill.moveTo(px, size.height); fill.lineTo(px, py) }
                else { val prev = series[i - 1]; line.lineTo(px, y(prev.price)); line.lineTo(px, py); fill.lineTo(px, y(prev.price)); fill.lineTo(px, py) }
            }
            fill.lineTo(x(series.last().ts), size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(PT.Blue.copy(alpha = .35f), Color.Transparent)))
            drawPath(line, PT.Blue, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            sel?.let { series.getOrNull(it) }?.let { p ->
                drawLine(Color.White.copy(alpha = .5f), Offset(x(p.ts), 0f), Offset(x(p.ts), size.height), 2f)
                drawCircle(Color.White, 10f, Offset(x(p.ts), y(p.price)))
                drawCircle(PT.Blue, 6f, Offset(x(p.ts), y(p.price)))
            }
        }
    }
}

private fun nearest(series: List<PricePoint>, start: Long, end: Long, x: Float, w: Float): Int? {
    if (series.isEmpty()) return null
    val t = start + ((end - start) * (x / w)).toLong()
    return series.indices.minByOrNull { Math.abs(series[it].ts - t) }
}
