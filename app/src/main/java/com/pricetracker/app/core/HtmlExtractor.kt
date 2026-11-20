package com.pricetracker.app.core

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class Extracted(val value: Double, val currency: String?, val title: String?)

data class SearchHit(val title: String, val price: Double, val currency: String?, val url: String)

object HtmlExtractor {
    fun title(doc: Document): String? =
        doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }.ifBlank { null }

    /** Price of a single product page. A CSS [selector] picked by the user wins; otherwise structured data. */
    fun extract(html: String, selector: String?, baseUrl: String? = null): Extracted? {
        val doc = if (baseUrl != null) Jsoup.parse(html, baseUrl) else Jsoup.parse(html)
        val title = title(doc)
        if (!selector.isNullOrBlank()) {
            runCatching { doc.select(selector).firstOrNull()?.text() }.getOrNull()?.let { PriceParser.parse(it) }?.let {
                return Extracted(it.value, it.currency, title)
            }
        }
        for (s in doc.select("script[type=application/ld+json]")) {
            val parsed = runCatching { JSONObject("{\"x\":" + s.data().trim() + "}").get("x") }.getOrNull()
            findOffer(parsed)?.let { return Extracted(it.first, it.second, title) }
        }
        val metaKeys = listOf("meta[property=product:price:amount]", "meta[property=og:price:amount]", "meta[itemprop=price]", "[itemprop=price]")
        for (k in metaKeys) {
            val el = doc.select(k).firstOrNull() ?: continue
            val v = el.attr("content").ifBlank { el.text() }
            PriceParser.toDouble(v.trim())?.let {
                val cur = doc.select("meta[property=product:price:currency],meta[property=og:price:currency],meta[itemprop=priceCurrency]")
                    .firstOrNull()?.attr("content")?.ifBlank { null }
                return Extracted(it, cur, title)
            }
        }
        return null
    }

    private fun findOffer(any: Any?): Pair<Double, String?>? {
        when (any) {
            is JSONObject -> {
                if (any.has("offers")) findOffer(any.get("offers"))?.let { return it }
                for (k in listOf("price", "lowPrice")) if (any.has(k)) {
                    PriceParser.toDouble(any.get(k).toString().trim())?.let {
                        return it to any.optString("priceCurrency").ifBlank { null }
                    }
                }
                for (k in listOf("@graph", "mainEntity", "itemListElement", "item")) if (any.has(k)) findOffer(any.get(k))?.let { return it }
            }
            is JSONArray -> for (i in 0 until any.length()) findOffer(any.get(i))?.let { return it }
        }
        return null
    }

    /** Best-effort parse of a store's search-results page into product hits (title + price + link). */
    fun searchHits(html: String, baseUrl: String, max: Int = 8): List<SearchHit> {
        val doc = Jsoup.parse(html, baseUrl)
        val hits = LinkedHashMap<String, SearchHit>()
        // 1) structured data item lists
        for (s in doc.select("script[type=application/ld+json]")) {
            val parsed = runCatching { JSONObject("{\"x\":" + s.data().trim() + "}").get("x") }.getOrNull() ?: continue
            collectLd(parsed, hits)
        }
        // 2) generic heuristic: price-looking leaf -> climb to a card holding a link and a title
        if (hits.size < 3) {
            for (el in doc.select("span,div,p,b,strong,bdi")) {
                if (hits.size >= max * 2) break
                if (el.children().size > 3) continue
                val t = el.ownText().ifBlank { el.text() }.trim()
                if (t.length > 40 || PriceParser.classify(t) != 2) continue
                val p = PriceParser.parse(t) ?: continue
                if (p.value <= 0) continue
                var card: Element? = el
                var link: Element? = null
                for (i in 0 until 6) {
                    card = card?.parent() ?: break
                    link = card.selectFirst("a[href]")
                    if (link != null && card.text().length > 25) break
                }
                if (card == null || link == null) continue
                val href = link.absUrl("href")
                if (href.isBlank() || href.startsWith("javascript")) continue
                val title = (card.selectFirst("img[alt]")?.attr("alt")?.takeIf { it.length > 8 }
                    ?: card.text().replace(t, "").trim()).take(120)
                if (title.length < 8) continue
                hits.putIfAbsent(href.substringBefore('#'), SearchHit(title, p.value, p.currency, href))
            }
        }
        return hits.values.take(max)
    }

    private fun collectLd(any: Any?, out: MutableMap<String, SearchHit>) {
        when (any) {
            is JSONObject -> {
                val offer = any.opt("offers")?.let { findOffer(it) } ?: findOffer(any.takeIf { it.has("price") })
                val name = any.optString("name")
                val url = any.optString("url")
                if (offer != null && name.isNotBlank() && url.startsWith("http")) out.putIfAbsent(url, SearchHit(name.take(120), offer.first, offer.second, url))
                for (k in any.keys()) { val v = any.get(k); if (v is JSONObject || v is JSONArray) collectLd(v, out) }
            }
            is JSONArray -> for (i in 0 until any.length()) collectLd(any.get(i), out)
        }
    }
}
