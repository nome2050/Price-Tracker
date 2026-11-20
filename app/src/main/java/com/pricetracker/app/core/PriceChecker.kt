package com.pricetracker.app.core

import android.content.Context
import com.pricetracker.app.R
import com.pricetracker.app.data.Graph
import com.pricetracker.app.data.TrackedItem

object PriceChecker {
    suspend fun preview(ctx: Context, url: String, selector: String? = null): Extracted? {
        val html = PageFetcher.http(url)
        html?.let { HtmlExtractor.extract(it, selector, url) }?.let { return it }
        val rendered = PageFetcher.rendered(ctx, url)
        rendered?.let { HtmlExtractor.extract(it, selector, url) }?.let { return it }
        // Last resort: let the AI read the page text (only for items without a user-picked selector).
        val src = rendered ?: html
        if (src != null && selector == null) Ai.extractPrice(ctx, src)?.let { return it }
        return null
    }

    suspend fun check(ctx: Context, item: TrackedItem): Boolean {
        val url = item.url ?: return false
        val e = preview(ctx, url, item.selector)
        return if (e != null) {
            Graph.repo.recordPrice(item.id, e.value, e.currency); true
        } else {
            Graph.repo.markError(item.id, ctx.getString(R.string.err_read_price)); false
        }
    }

    suspend fun checkAll(ctx: Context): Int {
        var ok = 0
        for (i in Graph.repo.dao.webItems()) if (check(ctx, i)) ok++
        return ok
    }
}
