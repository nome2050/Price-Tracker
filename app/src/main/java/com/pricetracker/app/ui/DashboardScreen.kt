package com.pricetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricetracker.app.R
import com.pricetracker.app.core.Ai
import com.pricetracker.app.core.Money
import com.pricetracker.app.data.PricePoint
import com.pricetracker.app.data.TrackedItem
import com.pricetracker.app.ui.theme.PT
import kotlin.math.abs

private val Cyan = Color(0xFF5CF2FF)
private val Violet = Color(0xFF8B6CFF)
private val PanelBg = Color(0xCC0E1424)
private val Edge = Brush.linearGradient(listOf(Cyan.copy(alpha = .45f), Violet.copy(alpha = .15f), Cyan.copy(alpha = .05f)))

private class Drop(val item: TrackedItem, val old: Double, val new: Double, val ts: Long)
private class Better(val item: TrackedItem, val store: String, val price: Double, val url: String, val pct: Double)

@Composable
fun DashboardScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val items by vm.items.collectAsState()
    val history by vm.history.collectAsState()
    val offers by vm.allOffers.collectAsState()
    val insight by vm.insight.collectAsState()
    val busy by vm.insightBusy.collectAsState()
    var range by remember { mutableIntStateOf(2) }
    val now = System.currentTimeMillis()

    val savings = remember(items, history) {
        items.sumOf { i ->
            val first = history[i.id]?.firstOrNull()?.price; val cur = i.currentPrice
            if (first != null && cur != null && first > cur) first - cur else 0.0
        }
    }
    val cur = remember(items) { items.mapNotNull { it.currency }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key }
    val drops = remember(items, history) {
        items.mapNotNull { i ->
            val pts = history[i.id].orEmpty()
            if (pts.size < 2) return@mapNotNull null
            val last = pts.last()
            val prev = pts.dropLast(1).lastOrNull { it.price != last.price } ?: return@mapNotNull null
            Drop(i, prev.price, last.price, last.ts)
        }.sortedByDescending { it.ts }
    }
    val drops24 = drops.count { it.new < it.old && it.ts > now - 86_400_000L }
    val atTarget = items.count { it.targetPrice != null && it.currentPrice != null && it.currentPrice <= it.targetPrice }
    val better = remember(items, offers) {
        items.mapNotNull { i ->
            val mine = i.currentPrice ?: return@mapNotNull null
            val o = offers.filter { it.itemId == i.id && it.currency == i.currency }.minByOrNull { it.price } ?: return@mapNotNull null
            if (o.price >= mine) null else Better(i, o.store, o.price, o.url, (mine - o.price) / mine * 100)
        }.sortedByDescending { it.pct }
    }
    val index = remember(history, items) { basketIndex(history) }

    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.radialGradient(listOf(Violet.copy(alpha = .22f), Color.Transparent), Offset(size.width, 0f), size.width * .9f))
        drawRect(Brush.radialGradient(listOf(Cyan.copy(alpha = .14f), Color.Transparent), Offset(0f, size.height * .1f), size.width * .8f))
    }) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Spacer(Modifier.height(48.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dash_title), style = MaterialTheme.typography.headlineLarge)
                        Mono(stringResource(R.string.dash_sub))
                    }
                    Box(Modifier.size(10.dp).background(PT.Down, RoundedCornerShape(50)))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Kpi(stringResource(R.string.kpi_tracked), items.size.toString(), Cyan, Modifier.weight(1f))
                    Kpi(stringResource(R.string.kpi_savings), Money.format(savings, cur), PT.Down, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Kpi(stringResource(R.string.kpi_drops), drops24.toString(), Violet, Modifier.weight(1f))
                    Kpi(stringResource(R.string.kpi_target), atTarget.toString(), PT.Blue, Modifier.weight(1f))
                }
            }
            item {
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dash_index), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(R.string.range_day, R.string.range_week, R.string.range_month).forEachIndexed { k, r ->
                                FilterChip(range == k, { range = k }, label = { Text(stringResource(r), fontSize = 11.sp) }, shape = RoundedCornerShape(50))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val span = listOf(86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L)[range]
                    GlowChart(index, now - span, now)
                    Mono(stringResource(R.string.dash_index_sub))
                }
            }
            item {
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dash_ai), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Cyan)
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else if (Ai.available(ctx)) FilledTonalButton(enabled = items.isNotEmpty(), onClick = {
                            vm.askPortfolio(items.map { i ->
                                val best = offers.filter { it.itemId == i.id }.minByOrNull { it.price }
                                "${i.name}: now ${i.currentPrice}, first ${history[i.id]?.firstOrNull()?.price}, target ${i.targetPrice}, cheapest rival ${best?.store} ${best?.price}"
                            })
                        }) { Text(stringResource(R.string.ai_ask)) }
                        else TextButton(onClick = onSettings) { Text(stringResource(R.string.dash_add_key)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(when { insight == null -> stringResource(R.string.dash_ai_hint); insight!!.isBlank() -> stringResource(R.string.ai_error); else -> insight!! },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Glass {
                    Text(stringResource(R.string.dash_cheaper), style = MaterialTheme.typography.titleMedium)
                    Mono(stringResource(R.string.dash_cheaper_sub))
                    Spacer(Modifier.height(8.dp))
                    if (better.isEmpty()) Text(stringResource(R.string.dash_cheaper_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    better.take(5).forEach { b ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(b.item.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(b.store, color = Cyan, style = MaterialTheme.typography.labelMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(b.price, b.item.currency), fontWeight = FontWeight.SemiBold)
                                Text("−" + String.format("%.0f%%", b.pct), color = PT.Down, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            item {
                Glass {
                    Text(stringResource(R.string.dash_drops), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (drops.isEmpty()) Text(stringResource(R.string.dash_drops_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    drops.take(6).forEach { d ->
                        val down = d.new < d.old
                        Row(Modifier.fillMaxWidth().clickable { onOpen(d.item.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Mono(Money.format(d.old, d.item.currency) + " → " + Money.format(d.new, d.item.currency))
                            }
                            val c = if (down) PT.Down else PT.Up
                            Text((if (down) "▼ " else "▲ ") + String.format("%.1f%%", abs((d.new - d.old) / d.old * 100)), color = c,
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.background(c.copy(alpha = .14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 100 = every tracked item at the first price we saw; moves as prices change. */
private fun basketIndex(history: Map<Long, List<PricePoint>>): List<Pair<Long, Double>> {
    val base = history.mapValues { it.value.firstOrNull()?.price ?: 0.0 }.filterValues { it > 0 }
    val cur = HashMap<Long, Double>()
    return history.values.flatten().filter { it.itemId in base }.sortedBy { it.ts }.map { p ->
        cur[p.itemId] = p.price / base[p.itemId]!!
        p.ts to cur.values.average() * 100
    }
}

@Composable
private fun GlowChart(series: List<Pair<Long, Double>>, start: Long, end: Long) {
    val carry = series.lastOrNull { it.first < start }
    val pts = buildList {
        if (carry != null) add(start to carry.second)
        addAll(series.filter { it.first >= start })
        if (isNotEmpty() && last().first < end) add(end to last().second)
    }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        if (pts.size < 2) { drawLine(Cyan.copy(alpha = .4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f, StrokeCap.Round); return@Canvas }
        val lo = pts.minOf { it.second } - 1; val hi = pts.maxOf { it.second } + 1
        fun x(t: Long) = size.width * (t - start).toFloat() / (end - start).coerceAtLeast(1)
        fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
        for (g in 0..3) drawLine(Color(0x1F78AAFF), Offset(0f, size.height * g / 3), Offset(size.width, size.height * g / 3), 2f)
        val line = Path(); val fill = Path()
        pts.forEachIndexed { i, p ->
            val px = x(p.first); val py = y(p.second)
            if (i == 0) { line.moveTo(px, py); fill.moveTo(px, size.height); fill.lineTo(px, py) } else { line.lineTo(px, py); fill.lineTo(px, py) }
        }
        fill.lineTo(x(pts.last().first), size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Cyan.copy(alpha = .30f), Violet.copy(alpha = 0f))))
        drawPath(line, Cyan.copy(alpha = .25f), style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, Cyan, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Cyan.copy(alpha = .3f), 16f, Offset(x(pts.last().first), y(pts.last().second)))
        drawCircle(Color.White, 6f, Offset(x(pts.last().first), y(pts.last().second)))
    }
}

@Composable
private fun Glass(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = PanelBg, border = BorderStroke(1.dp, Edge)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun Kpi(label: String, value: String, accent: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = PanelBg, border = BorderStroke(1.dp, Edge)) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.width(28.dp).height(2.dp).background(accent))
            Spacer(Modifier.height(10.dp))
            Mono(label)
            Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
        }
    }
}

@Composable
private fun Mono(text: String) =
    Text(text.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
