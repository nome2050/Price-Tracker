package com.pricetracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pricetracker.app.R
import com.pricetracker.app.core.Money
import com.pricetracker.app.data.TrackedItem
import com.pricetracker.app.ui.theme.PT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onBrowser: () -> Unit, onSettings: () -> Unit) {
    val items by vm.items.collectAsState()
    val history by vm.history.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(56.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
                        Text(pluralStringResource(R.plurals.home_subtitle, items.size, items.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (refreshing) CircularProgressIndicator(Modifier.size(28.dp).padding(end = 4.dp), strokeWidth = 3.dp)
                    else FilledTonalIconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (items.isEmpty()) item { EmptyState(onBrowser, onSettings) { showAdd = true } }
            items(items, key = { it.id }) { ItemCard(it, history[it.id].orEmpty().map { p -> p.price }, history[it.id]?.firstOrNull()?.price) { onOpen(it.id) } }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add)) },
            containerColor = PT.Blue, contentColor = androidx.compose.ui.graphics.Color.Black,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    if (showAdd) AddSheet(vm, onDismiss = { showAdd = false }, onAdded = { showAdd = false; onOpen(it) })
}

@Composable
private fun ItemCard(item: TrackedItem, spark: List<Double>, first: Double?, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(sourceLabel(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.currentPrice?.let { Money.format(it, item.currency) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                    ChangeChip(first, item.currentPrice)
                }
                item.targetPrice?.let {
                    Text(stringResource(R.string.target_fmt, Money.format(it, item.currency)), style = MaterialTheme.typography.labelMedium, color = PT.Blue)
                }
            }
            SparkLine(spark, Modifier.width(84.dp).height(48.dp))
        }
    }
}

@Composable
fun sourceLabel(item: TrackedItem): String = when (item.source) {
    "APP" -> stringResource(R.string.src_app, item.appLabel ?: item.packageName ?: "")
    "WEB" -> runCatching { android.net.Uri.parse(item.url).host?.removePrefix("www.") }.getOrNull() ?: stringResource(R.string.src_web)
    else -> stringResource(R.string.src_manual)
}

@Composable
private fun EmptyState(onBrowser: () -> Unit, onSettings: () -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        Text(stringResource(R.string.empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        HowCard(stringResource(R.string.empty_a11y_title), stringResource(R.string.empty_a11y_body), onSettings)
        HowCard(stringResource(R.string.empty_web_title), stringResource(R.string.empty_web_body), onBrowser)
        HowCard(stringResource(R.string.empty_link_title), stringResource(R.string.empty_link_body), onAdd)
    }
}

@Composable
private fun HowCard(title: String, body: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(vm: MainViewModel, onDismiss: () -> Unit, onAdded: (Long) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.add_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.add_link)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.add_name)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(price, { price = it }, label = { Text(stringResource(R.string.add_price)) }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.add_target)) }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
            }
            Text(stringResource(R.string.add_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = !busy && (url.isNotBlank() || name.isNotBlank()),
                onClick = {
                    busy = true
                    vm.add(url, name, price.replace(',', '.').toDoubleOrNull(), target.replace(',', '.').toDoubleOrNull()) { id, _ -> onAdded(id) }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.Black) else Text(stringResource(R.string.add_save)) }
        }
    }
}
