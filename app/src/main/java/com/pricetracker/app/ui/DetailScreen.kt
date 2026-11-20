package com.pricetracker.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pricetracker.app.R
import com.pricetracker.app.core.Money
import com.pricetracker.app.data.Offer
import com.pricetracker.app.data.PricePoint
import com.pricetracker.app.ui.theme.PT
import java.text.DateFormat
import java.util.Date

private enum class Range(val labelRes: Int, val ms: Long) {
    Day(R.string.range_day, 86_400_000L), Week(R.string.range_week, 7 * 86_400_000L),
    Month(R.string.range_month, 30 * 86_400_000L), Year(R.string.range_year, 365 * 86_400_000L), All(R.string.range_all, 0L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: Long, vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val item by remember(id) { vm.repo.item(id) }.collectAsState(initial = null)
    val points by remember(id) { vm.repo.points(id) }.collectAsState(initial = emptyList())
    val offers by remember(id) { vm.repo.offers(id) }.collectAsState(initial = emptyList())
    val refreshing by vm.refreshing.collectAsState()
    val comparing by vm.comparing.collectAsState()
    val it0 = item ?: return
    var range by remember { mutableStateOf(Range.Month) }
    var target by remember(it0.id) { mutableStateOf(it0.targetPrice?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var logDialog by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val start = if (range == Range.All) (points.firstOrNull()?.ts ?: (now - 86_400_000L)) else now - range.ms
    val visible = points.filter { it.ts >= start }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(it0.name, style = MaterialTheme.typography.titleLarge, maxLines = 3)
                Text(sourceLabel(it0), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(it0.currentPrice?.let { p -> Money.format(p, it0.currency) } ?: "—", style = MaterialTheme.typography.headlineLarge)
                    ChangeChip(visible.firstOrNull()?.price ?: points.firstOrNull()?.price, it0.currentPrice)
                }
                it0.lastChecked?.let { Text(stringResource(R.string.checked_fmt, df.format(Date(it))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                it0.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PT.Up) }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Range.values().forEach { r ->
                            FilterChip(range == r, { range = r }, label = { Text(stringResource(r.labelRes)) }, shape = RoundedCornerShape(50))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    PriceChart(points, start, now, it0.currency)
                }
            }
        }
        item {
            val ps = visible.map { it.price }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(stringResource(R.string.lowest), ps.minOrNull(), it0.currency, Modifier.weight(1f))
                Stat(stringResource(R.string.average), ps.takeIf { it.isNotEmpty() }?.average(), it0.currency, Modifier.weight(1f))
                Stat(stringResource(R.string.highest), ps.maxOrNull(), it0.currency, Modifier.weight(1f))
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.target_title), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(target, { target = it }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                            placeholder = { Text(stringResource(R.string.target_hint)) })
                        FilledTonalButton(onClick = { vm.setTarget(it0, target.replace(',', '.').toDoubleOrNull()) }) { Text(stringResource(R.string.set)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (it0.source == "WEB") OutlinedButton(onClick = { vm.check(it0) }, enabled = !refreshing) { Text(stringResource(R.string.check_now)) }
                        OutlinedButton(onClick = { logDialog = true }) { Text(stringResource(R.string.log_price)) }
                        OutlinedButton(onClick = { open(ctx, it0.url, it0.packageName) }, enabled = it0.url != null || it0.packageName != null) { Text(stringResource(R.string.open)) }
                    }
                }
            }
        }
        // ---------- Product 360 ----------
        item { Product360(it0.id, offers, it0.currentPrice, it0.currency, id in comparing, onRefresh = { vm.compare(id) }, onOpen = { u -> open(ctx, u, null) }) }

        item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
        items(points.asReversed().take(60)) { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                Text(df.format(Date(p.ts)), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(Money.format(p.price, it0.currency), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.delete_q)) },
        confirmButton = { TextButton(onClick = { vm.delete(id); onBack() }) { Text(stringResource(R.string.delete), color = PT.Up) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (logDialog) {
        var v by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { logDialog = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.log_price)) },
            text = { OutlinedTextField(v, { v = it }, singleLine = true, label = { Text(stringResource(R.string.add_price)) }) },
            confirmButton = { TextButton(onClick = { v.replace(',', '.').toDoubleOrNull()?.let { vm.logPrice(id, it) }; logDialog = false }) { Text(stringResource(R.string.add_save)) } },
            dismissButton = { TextButton(onClick = { logDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Stat(label: String, v: Double?, cur: String?, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(v?.let { Money.format(it, cur) } ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun Product360(itemId: Long, offers: List<Offer>, mine: Double?, cur: String?, busy: Boolean, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.p360_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.p360_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                else FilledTonalButton(onClick = onRefresh) { Text(stringResource(R.string.p360_search)) }
            }
            if (offers.isEmpty() && !busy) Text(stringResource(R.string.p360_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            val best = offers.firstOrNull()
            offers.take(12).forEach { o ->
                Surface(onClick = { onOpen(o.url) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(o.store, style = MaterialTheme.typography.labelLarge, color = PT.Blue)
                                if (o == best) Text(stringResource(R.string.p360_best), style = MaterialTheme.typography.labelSmall, color = PT.Down)
                            }
                            Text(o.title, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(o.price, o.currency), style = MaterialTheme.typography.titleMedium)
                            if (mine != null && o.currency == cur && mine > 0) {
                                val d = (o.price - mine) / mine * 100
                                Text(String.format("%+.0f%%", d), style = MaterialTheme.typography.labelSmall, color = if (d < 0) PT.Down else PT.Up)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun open(ctx: android.content.Context, url: String?, pkg: String?) {
    val i = when {
        url != null -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
        pkg != null -> ctx.packageManager.getLaunchIntentForPackage(pkg)
        else -> null
    } ?: return
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
