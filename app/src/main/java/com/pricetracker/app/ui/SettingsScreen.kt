package com.pricetracker.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pricetracker.app.R
import com.pricetracker.app.a11y.PriceAccessibilityService
import com.pricetracker.app.core.Prefs
import com.pricetracker.app.ui.theme.PT
import com.pricetracker.app.work.Scheduler

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(PriceAccessibilityService.isEnabled(ctx)) }
    LifecycleResumeEffect(Unit) { enabled = PriceAccessibilityService.isEnabled(ctx); onPauseOrDispose { } }
    var bubble by remember { mutableStateOf(Prefs.bubble(ctx)) }
    var alerts by remember { mutableStateOf(Prefs.alerts(ctx)) }
    var auto by remember { mutableStateOf(Prefs.autoCompare(ctx)) }
    var interval by remember { mutableIntStateOf(Prefs.intervalMinutes(ctx)) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(56.dp)); Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 8.dp)) }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_a11y), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (enabled) R.string.set_a11y_on else R.string.set_a11y_off), color = if (enabled) PT.Down else PT.Up, style = MaterialTheme.typography.labelLarge)
                    }
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(stringResource(R.string.set_open_a11y)) }
                }
                Text(stringResource(R.string.set_a11y_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                    Text(stringResource(R.string.set_app_info))
                }
            }
        }
        item { Card { SwitchRow(stringResource(R.string.set_bubble), stringResource(R.string.set_bubble_sub), bubble) { bubble = it; Prefs.setBubble(ctx, it); PriceAccessibilityService.instance?.applyBubble() } } }
        item { Card { Text(stringResource(R.string.set_tile), style = MaterialTheme.typography.bodyMedium) } }
        item {
            Card {
                Text(stringResource(R.string.set_interval), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.set_interval_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 180, 360, 720, 1440).forEach { m ->
                        FilterChip(interval == m, { interval = m; Prefs.setInterval(ctx, m); Scheduler.schedulePeriodic(ctx) },
                            label = { Text(if (m < 60) "$m m" else "${m / 60} h") })
                    }
                }
            }
        }
        item {
            Card {
                SwitchRow(stringResource(R.string.set_alerts), stringResource(R.string.set_alerts_sub), alerts) { alerts = it; Prefs.setAlerts(ctx, it) }
                SwitchRow(stringResource(R.string.set_autocompare), stringResource(R.string.set_autocompare_sub), auto) { auto = it; Prefs.setAutoCompare(ctx, it) }
            }
        }
        item {
            var key by remember { mutableStateOf(Prefs.groqKey(ctx)) }
            Card {
                Text(stringResource(R.string.set_ai), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.set_ai_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(key, { key = it; Prefs.setGroqKey(ctx, it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium, label = { Text(stringResource(R.string.set_ai_key)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            }
        }
        item {
            Card {
                Text(stringResource(R.string.set_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.set_language_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    val i = if (Build.VERSION.SDK_INT >= 33) Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${ctx.packageName}")) else Intent(Settings.ACTION_LOCALE_SETTINGS)
                    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text(stringResource(R.string.set_language_btn)) }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(v, onChange)
    }
}
