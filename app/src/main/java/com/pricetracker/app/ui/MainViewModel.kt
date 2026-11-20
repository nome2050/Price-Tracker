package com.pricetracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pricetracker.app.core.Compare
import com.pricetracker.app.core.Prefs
import com.pricetracker.app.core.PriceChecker
import com.pricetracker.app.data.*
import com.pricetracker.app.work.Scheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val repo = Graph.repo
    val items = repo.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<Map<Long, List<PricePoint>>> =
        repo.pointsSince(System.currentTimeMillis() - 30L * 86_400_000).map { l -> l.groupBy { it.itemId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val allOffers: StateFlow<List<Offer>> = repo.allOffers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val insight = MutableStateFlow<String?>(null)
    val insightBusy = MutableStateFlow(false)
    fun askPortfolio(lines: List<String>) = viewModelScope.launch {
        insightBusy.value = true
        insight.value = com.pricetracker.app.core.Ai.portfolio(app, lines) ?: ""
        insightBusy.value = false
    }

    val refreshing = MutableStateFlow(false)
    val comparing = MutableStateFlow<Set<Long>>(emptySet())

    fun refreshAll() = viewModelScope.launch {
        refreshing.value = true
        runCatching { PriceChecker.checkAll(app) }
        refreshing.value = false
    }

    fun check(item: TrackedItem) = viewModelScope.launch {
        refreshing.value = true
        runCatching { PriceChecker.check(app, item) }
        refreshing.value = false
    }

    fun compare(id: Long) = viewModelScope.launch {
        comparing.value = comparing.value + id
        runCatching { Compare.run(app, id) }
        comparing.value = comparing.value - id
    }

    fun normalizeUrl(s: String): String? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val u = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
        return runCatching { Uri.parse(u).takeIf { !it.host.isNullOrBlank() && it.host!!.contains('.') }?.toString() }.getOrNull()
    }

    /** Adds an item (fetching its price from [url] if needed) and kicks off Product 360 in the background. */
    fun add(url: String, name: String, price: Double?, target: Double?, selector: String? = null,
            currency: String? = null, onDone: (Long, Boolean) -> Unit = { _, _ -> }) = viewModelScope.launch {
        val clean = normalizeUrl(url)
        var n = name; var p = price; var cur = currency
        if (clean != null && p == null) {
            PriceChecker.preview(app, clean, selector)?.let { e -> p = e.value; cur = cur ?: e.currency; if (n.isBlank()) n = e.title ?: "" }
        }
        if (n.isBlank()) n = clean?.let { Uri.parse(it).host } ?: "Item"
        val id = repo.addItem(TrackedItem(name = n.take(120), source = if (clean != null) "WEB" else "MANUAL", url = clean,
            selector = selector, currency = cur, targetPrice = target), p)
        if (Prefs.autoCompare(app)) Scheduler.compare(app, id)
        onDone(id, p != null)
    }

    val advice = MutableStateFlow<Map<Long, String>>(emptyMap())
    val advising = MutableStateFlow(false)
    fun askAdvice(item: TrackedItem, points: List<PricePoint>, offers: List<Offer>) = viewModelScope.launch {
        advising.value = true
        val r = com.pricetracker.app.core.Ai.advice(app, item.name, points.map { it.ts to it.price }, item.currency,
            offers.map { "${it.store} ${it.price}" }, item.targetPrice)
        advice.value = advice.value + (item.id to (r ?: ""))
        advising.value = false
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun setTarget(item: TrackedItem, t: Double?) = viewModelScope.launch { repo.update(item.copy(targetPrice = t)) }
    fun rename(item: TrackedItem, n: String) = viewModelScope.launch { repo.update(item.copy(name = n)) }
    fun logPrice(id: Long, p: Double) = viewModelScope.launch { repo.recordPrice(id, p) }
}
