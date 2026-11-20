package com.pricetracker.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Groq (OpenAI-compatible) client. The API key is entered by the user in Settings and stored only on the
 * device; it is never part of the source code or the APK. Every feature works without it, just less smartly.
 */
object Ai {
    private const val URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

    fun available(ctx: Context) = Prefs.groqKey(ctx).isNotBlank()

    private suspend fun chat(ctx: Context, system: String, user: String, json: Boolean): String? = withContext(Dispatchers.IO) {
        val key = Prefs.groqKey(ctx)
        if (key.isBlank()) return@withContext null
        runCatching {
            val body = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.1)
                put("max_tokens", 700)
                if (json) put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)))
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(URL).header("Authorization", "Bearer $key").post(body).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                JSONObject(r.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
        }.getOrNull()
    }

    /** Reads a product page's text when normal parsing found no price. */
    suspend fun extractPrice(ctx: Context, html: String): Extracted? {
        if (!available(ctx)) return null
        val doc = Jsoup.parse(html)
        val text = (doc.title() + "\n" + doc.body().text()).take(6000)
        val out = chat(ctx,
            "You extract the current selling price of the main product on a web page. Reply with JSON only: " +
                "{\"title\":string,\"price\":number or null,\"currency\":ISO 4217 code or null}. Ignore crossed-out, installment and related-product prices.",
            text, true) ?: return null
        return runCatching {
            val o = JSONObject(out)
            if (o.isNull("price")) return null
            Extracted(o.getDouble("price"), o.optString("currency").takeIf { it.length == 3 }, o.optString("title").ifBlank { null })
        }.getOrNull()
    }

    /** Returns indices of [candidates] that are the same product as [product], or null if the AI is unavailable. */
    suspend fun sameProduct(ctx: Context, product: String, candidates: List<String>): Set<Int>? {
        val list = candidates.mapIndexed { i, c -> "$i: $c" }.joinToString("\n").take(9000)
        val out = chat(ctx,
            "You match shopping search results to a product. A result matches only if it is the same product: same brand, model, " +
                "variant, size or capacity. Exclude accessories, cases, spare parts, bundles and different models. " +
                "Reply with JSON only: {\"same\":[indices]}.",
            "Product: $product\nResults (index: title | price):\n$list", true) ?: return null
        return runCatching {
            val a = JSONObject(out).getJSONArray("same")
            (0 until a.length()).map { a.getInt(it) }.toSet()
        }.getOrNull()
    }

    /** Short buy-or-wait advice from price history and rival store prices, in the phone's language. */
    suspend fun advice(ctx: Context, name: String, history: List<Pair<Long, Double>>, currency: String?, offers: List<String>, target: Double?): String? {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val h = history.takeLast(60).joinToString("; ") { df.format(java.util.Date(it.first)) + " " + it.second }
        val lang = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
        return chat(ctx,
            "You are a concise shopping advisor. Using only the data given, say whether to buy now or wait, in at most 3 short sentences, " +
                "and mention the cheapest rival store if it is meaningfully cheaper. Do not invent data. Answer in $lang.",
            "Product: $name\nCurrency: ${currency ?: "unknown"}\nTarget price: ${target ?: "none"}\nPrice history (date price): $h\nOther stores: ${offers.take(8).joinToString("; ").ifBlank { "none" }}",
            false)?.trim()
    }
}
