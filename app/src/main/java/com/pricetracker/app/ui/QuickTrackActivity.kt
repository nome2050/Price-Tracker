package com.pricetracker.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pricetracker.app.R
import com.pricetracker.app.core.PriceChecker
import com.pricetracker.app.core.PriceParser
import com.pricetracker.app.core.Prefs
import com.pricetracker.app.data.Graph
import com.pricetracker.app.data.TrackedItem
import com.pricetracker.app.ui.theme.PriceTrackerTheme
import com.pricetracker.app.work.Scheduler
import kotlinx.coroutines.launch

/**
 * Small bottom-sheet style screen used by: the tap-a-price overlay, the "Track price" entry in the
 * text-selection menu (any app/browser) and "Share" from any app/browser.
 */
class QuickTrackActivity : ComponentActivity() {
    companion object {
        const val ACTION_TRACK = "com.pricetracker.app.TRACK"
        const val EXTRA_PRICE = "price"; const val EXTRA_CURRENCY = "currency"; const val EXTRA_NAME = "name"
        const val EXTRA_PKG = "pkg"; const val EXTRA_APP_LABEL = "appLabel"; const val EXTRA_VIEW_ID = "viewId"
        const val EXTRA_CONTEXT = "context"; const val EXTRA_URL = "url"
        private val URL_RE = Regex("https?://\\S+")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var price: Double? = null; var cur: String? = null; var name = ""; var url: String? = null
        var pkg: String? = null; var label: String? = null; var viewId: String? = null; var ctxText: String? = null
        when (intent.action) {
            ACTION_TRACK -> {
                if (intent.hasExtra(EXTRA_PRICE)) price = intent.getDoubleExtra(EXTRA_PRICE, 0.0)
                cur = intent.getStringExtra(EXTRA_CURRENCY); name = intent.getStringExtra(EXTRA_NAME) ?: ""
                pkg = intent.getStringExtra(EXTRA_PKG); label = intent.getStringExtra(EXTRA_APP_LABEL)
                viewId = intent.getStringExtra(EXTRA_VIEW_ID); ctxText = intent.getStringExtra(EXTRA_CONTEXT); url = intent.getStringExtra(EXTRA_URL)
            }
            Intent.ACTION_SEND -> {
                val t = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                url = URL_RE.find(t)?.value
                val rest = t.replace(URL_RE, "").trim()
                PriceParser.parse(rest)?.let { price = it.value; cur = it.currency }
                name = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE) ?: rest.take(80)
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val t = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
                PriceParser.parse(t)?.let { price = it.value; cur = it.currency }
            }
        }
        val i0 = Init(price, cur, name, url, pkg, label, viewId, ctxText)
        setContent { PriceTrackerTheme { Sheet(i0) } }
    }

    private class Init(val price: Double?, val cur: String?, val name: String, val url: String?, val pkg: String?,
                       val label: String?, val viewId: String?, val ctx: String?)

    @Composable
    private fun Sheet(i: Init) {
        var name by remember { mutableStateOf(i.name) }
        var price by remember { mutableStateOf(i.price?.toString() ?: "") }
        var url by remember { mutableStateOf(i.url ?: "") }
        var target by remember { mutableStateOf("") }
        var cur by remember { mutableStateOf(i.cur) }
        var loading by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (i.price == null && url.isNotBlank()) {
                loading = true
                PriceChecker.preview(this@QuickTrackActivity, url)?.let { e -> price = e.value.toString(); cur = cur ?: e.currency; if (name.isBlank()) name = e.title ?: "" }
                loading = false
            }
        }
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { finish() }) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.qt_title), style = MaterialTheme.typography.titleLarge)
                    if (i.label != null) Text(stringResource(R.string.src_app, i.label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.add_name)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(price, { price = it }, singleLine = true, label = { Text(if (loading) stringResource(R.string.qt_loading) else stringResource(R.string.add_price)) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                        OutlinedTextField(target, { target = it }, singleLine = true, label = { Text(stringResource(R.string.add_target)) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                    }
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.add_link)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    Button(modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !loading, onClick = { save(i, name, price, target, url, cur) }) { Text(stringResource(R.string.qt_track)) }
                }
            }
        }
    }

    private fun save(i: Init, name: String, price: String, target: String, url: String, cur: String?) {
        val p = price.replace(',', '.').toDoubleOrNull()
        val clean = url.trim().takeIf { it.isNotEmpty() }?.let { if (it.startsWith("http")) it else "https://$it" }
        val source = when { clean != null -> "WEB"; i.pkg != null -> "APP"; else -> "MANUAL" }
        lifecycleScope.launch {
            val id = Graph.repo.addItem(TrackedItem(
                name = name.ifBlank { i.label ?: clean ?: "Item" }.take(120), source = source, url = clean,
                packageName = i.pkg, appLabel = i.label, viewId = i.viewId, contextText = i.ctx, currency = cur,
                targetPrice = target.replace(',', '.').toDoubleOrNull()), p)
            if (Prefs.autoCompare(this@QuickTrackActivity)) Scheduler.compare(this@QuickTrackActivity, id)
            Toast.makeText(this@QuickTrackActivity, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
