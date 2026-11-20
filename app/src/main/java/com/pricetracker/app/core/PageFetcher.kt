package com.pricetracker.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object PageFetcher {
    const val UA = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    suspend fun http(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9").build())
                .execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    /** Loads the page in a headless WebView so JavaScript-rendered prices are available. */
    suspend fun rendered(ctx: Context, url: String, settleMs: Long = 3500): String? =
        withTimeoutOrNull(35_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val main = Handler(Looper.getMainLooper())
                    val wv = WebView(ctx.applicationContext)
                    var done = false
                    fun finish(html: String?) {
                        if (done) return
                        done = true
                        wv.stopLoading(); wv.destroy()
                        if (cont.isActive) cont.resume(html)
                    }
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.userAgentString = UA
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView, u: String?) {
                            v.postDelayed({
                                if (!done) v.evaluateJavascript("document.documentElement.outerHTML") { r ->
                                    finish(runCatching { if (r == null || r == "null") null else JSONTokener(r).nextValue() as? String }.getOrNull())
                                }
                            }, settleMs)
                        }
                    }
                    cont.invokeOnCancellation { main.post { if (!done) { done = true; wv.destroy() } } }
                    wv.loadUrl(url)
                }
            }
        }
}
