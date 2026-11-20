package com.pricetracker.app.core

import android.content.Context
import android.net.Uri
import com.pricetracker.app.data.Graph
import com.pricetracker.app.data.Offer

data class Store(val name: String, val region: String, val searchUrl: (String) -> String, val currency: String?)

/** Product 360: finds the same product on KSA stores and global marketplaces. */
object Compare {
    private fun q(s: String) = Uri.encode(s)
    val stores = listOf(
        Store("Amazon.sa", "KSA", { "https://www.amazon.sa/s?k=${q(it)}" }, "SAR"),
        Store("Noon", "KSA", { "https://www.noon.com/saudi-en/search/?q=${q(it)}" }, "SAR"),
        Store("Jarir", "KSA", { "https://www.jarir.com/sa-en/catalogsearch/result/?q=${q(it)}" }, "SAR"),
        Store("Extra", "KSA", { "https://www.extra.com/en-sa/search/?text=${q(it)}" }, "SAR"),
        Store("Sharaf DG", "KSA", { "https://ksa.sharafdg.com/?q=${q(it)}&post_type=product" }, "SAR"),
        Store("Lulu", "KSA", { "https://www.luluhypermarket.com/en-sa/search?q=${q(it)}" }, "SAR"),
        Store("Carrefour", "KSA", { "https://www.carrefourksa.com/mafsau/en/search?keyword=${q(it)}" }, "SAR"),
        Store("Nahdi", "KSA", { "https://www.nahdionline.com/en/search?q=${q(it)}" }, "SAR"),
        Store("AliExpress", "Global", { "https://www.aliexpress.com/wholesale?SearchText=${q(it)}" }, null),
        Store("eBay", "Global", { "https://www.ebay.com/sch/i.html?_nkw=${q(it)}" }, null),
        Store("Google Shopping", "Global", { "https://www.google.com/search?tbm=shop&q=${q(it)}&gl=sa" }, null),
    )

    fun cleanQuery(name: String): String =
        name.replace(Regex("[\\[\\](){}|,:;–—-]"), " ").replace(Regex("\\s+"), " ").trim().split(' ').take(8).joinToString(" ")

    private fun tokens(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()

    private fun relevance(query: String, title: String): Double {
        val a = tokens(query); if (a.isEmpty()) return 0.0
        val b = tokens(title)
        return a.count { it in b }.toDouble() / a.size
    }

    /** Searches every store, stores matching offers for [itemId]. Returns number of offers found. */
    suspend fun run(ctx: Context, itemId: Long): Int {
        val item = Graph.repo.get(itemId) ?: return 0
        val query = cleanQuery(item.name)
        if (query.isBlank()) return 0
        val now = System.currentTimeMillis()
        val aiOn = Ai.available(ctx)
        val all = ArrayList<Pair<Store, SearchHit>>()
        for (s in stores) {
            val url = s.searchUrl(query)
            val html = PageFetcher.rendered(ctx, url, 4500) ?: PageFetcher.http(url) ?: continue
            val hits = HtmlExtractor.searchHits(html, url, 12)
                .filter { relevance(query, it.title) >= (if (aiOn) 0.34 else 0.5) }
                .sortedBy { it.price }.take(if (aiOn) 5 else 3)
            hits.forEach { all += s to it }
        }
        // AI decides which candidates are really the same product (same model, size, capacity; not accessories).
        val keep = if (aiOn && all.isNotEmpty()) Ai.sameProduct(ctx, item.name, all.map { it.second.title + " | " + it.second.price }) else null
        val chosen = if (keep != null) all.filterIndexed { i, _ -> i in keep } else all
        val found = chosen.groupBy { it.first }.flatMap { (s, l) ->
            l.map { it.second }.sortedBy { it.price }.take(3).map { h ->
                Offer(itemId = itemId, store = s.name, title = h.title, price = h.price, currency = h.currency ?: s.currency, url = h.url, ts = now)
            }
        }
        Graph.repo.replaceOffers(itemId, found)
        return found.size
    }
}
