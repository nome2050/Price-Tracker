package com.pricetracker.app.a11y

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.Toast
import com.pricetracker.app.R
import com.pricetracker.app.core.ParsedPrice
import com.pricetracker.app.core.PriceParser
import com.pricetracker.app.core.Prefs
import com.pricetracker.app.data.Graph
import com.pricetracker.app.data.TrackedItem
import com.pricetracker.app.ui.QuickTrackActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class Candidate(
    val rect: Rect, val parsed: ParsedPrice, val text: String, val strong: Boolean,
    val viewId: String?, val node: AccessibilityNodeInfo,
) { var context: String? = null }

/**
 * Lets the user tap any price/number in any app (or browser) to track it, and silently
 * records new values of already-tracked app prices whenever they appear on screen.
 */
class PriceAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: PriceAccessibilityService? = null
        fun isEnabled(ctx: Context): Boolean {
            val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return s.contains(ctx.packageName + "/")
        }
        private val URL_BAR = Regex("(url_bar|location_bar_edit_text|toolbar_url|url_view|urlbar|omnibox)", RegexOption.IGNORE_CASE)
        private val BROWSERS = setOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser",
            "com.microsoft.emmx", "com.opera.browser", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var overlay: ScanOverlayView? = null
    private var appItems: Map<String, List<TrackedItem>> = emptyMap()
    private var lastAuto = 0L
    private val autoSeen = HashMap<Long, Long>()

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        applyBubble()
        scope.launch { Graph.repo.appItems.collect { l -> appItems = l.filter { it.packageName != null }.groupBy { it.packageName!! } } }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        removeOverlay(); removeBubble(); scope.cancel(); instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || overlay != null) return
        if (appItems[pkg].isNullOrEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAuto < 4000) return
        lastAuto = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val cands = collect(root)
        for (item in appItems[pkg].orEmpty()) {
            val m = cands.filter { item.viewId == null || it.viewId == item.viewId }.firstOrNull { c ->
                val want = item.contextText
                want.isNullOrBlank() || contextOf(c)?.let { it.contains(want, true) || want.contains(it, true) } == true
            } ?: continue
            if (now - (autoSeen[item.id] ?: 0) < 60_000 && item.currentPrice == m.parsed.value) continue
            autoSeen[item.id] = now
            scope.launch(Dispatchers.IO) { Graph.repo.recordPrice(item.id, m.parsed.value, m.parsed.currency) }
        }
    }

    // ---------- scanning ----------
    private fun collect(root: AccessibilityNodeInfo): List<Candidate> {
        val out = ArrayList<Candidate>()
        var count = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 80 || count++ > 6000 || !n.isVisibleToUser) return
            val t = (n.text ?: n.contentDescription)?.toString()
            if (!t.isNullOrBlank()) {
                val cls = PriceParser.classify(t)
                if (cls > 0) PriceParser.parse(t)?.let { p ->
                    val r = Rect(); n.getBoundsInScreen(r)
                    if (r.width() > 4 && r.height() > 4 && out.none { it.rect == r })
                        out += Candidate(r, p, t, cls == 2, n.viewIdResourceName, n)
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return out
    }

    private fun contextOf(c: Candidate): String? {
        c.context?.let { return it }
        var cur: AccessibilityNodeInfo? = c.node.parent
        var level = 0
        var best: String? = null
        while (cur != null && level < 3 && best == null) {
            val texts = ArrayList<String>()
            fun gather(n: AccessibilityNodeInfo?, budget: IntArray) {
                if (n == null || budget[0]-- <= 0) return
                (n.text ?: n.contentDescription)?.toString()?.let { texts += it }
                for (i in 0 until n.childCount) gather(n.getChild(i), budget)
            }
            gather(cur, intArrayOf(150))
            best = texts.filter { it.length >= 6 && PriceParser.classify(it) == 0 }.maxByOrNull { it.length }?.take(80)
            cur = cur.parent; level++
        }
        c.context = best
        return best
    }

    private fun findUrl(root: AccessibilityNodeInfo): String? {
        var found: String? = null
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || found != null || d > 40) return
            if (n.viewIdResourceName?.let { URL_BAR.containsMatchIn(it) } == true) {
                val t = n.text?.toString()?.trim()
                if (!t.isNullOrBlank() && !t.contains(' ')) { found = if (t.startsWith("http")) t else "https://$t"; return }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return found
    }

    private fun appLabel(pkg: String?): String? = pkg?.let {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
    }

    // ---------- public actions ----------
    fun scanLater(delayMs: Long) {
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        handler.postDelayed({ showScan() }, delayMs)
    }

    fun showScan() {
        removeOverlay()
        val root = rootInActiveWindow
        if (root == null) { toast(R.string.scan_none); return }
        val pkg = root.packageName?.toString()
        val cands = collect(root).filter { pkg != "com.android.systemui" }
        if (cands.isEmpty()) { toast(R.string.scan_none); return }
        cands.forEach { contextOf(it) }
        val url = if (pkg in BROWSERS) findUrl(root) else null
        val v = ScanOverlayView(this, cands, getString(R.string.scan_hint), onPick = { c -> removeOverlay(); pick(c, pkg, url) }, onClose = { removeOverlay() })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(v, lp)
        overlay = v
        bubble?.visibility = View.GONE
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        bubble?.visibility = View.VISIBLE
    }

    private fun pick(c: Candidate, pkg: String?, url: String?) {
        val label = appLabel(pkg)
        val i = Intent(this, QuickTrackActivity::class.java).apply {
            action = QuickTrackActivity.ACTION_TRACK
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(QuickTrackActivity.EXTRA_PRICE, c.parsed.value)
            putExtra(QuickTrackActivity.EXTRA_CURRENCY, c.parsed.currency)
            putExtra(QuickTrackActivity.EXTRA_NAME, c.context ?: label)
            putExtra(QuickTrackActivity.EXTRA_PKG, pkg)
            putExtra(QuickTrackActivity.EXTRA_APP_LABEL, label)
            putExtra(QuickTrackActivity.EXTRA_VIEW_ID, c.viewId)
            putExtra(QuickTrackActivity.EXTRA_CONTEXT, c.context)
            putExtra(QuickTrackActivity.EXTRA_URL, url)
        }
        runCatching { startActivity(i) }.onFailure {
            scope.launch(Dispatchers.IO) {
                Graph.repo.addItem(TrackedItem(name = c.context ?: label ?: "Item", source = "APP", url = url, packageName = pkg,
                    appLabel = label, viewId = c.viewId, contextText = c.context, currency = c.parsed.currency), c.parsed.value)
            }
            toast(R.string.saved)
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    // ---------- floating bubble ----------
    fun applyBubble() {
        if (Prefs.bubble(this)) showBubble() else removeBubble()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubble != null) return
        val size = dp(52)
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_tag); setColorFilter(Color.WHITE); setPadding(dp(13), dp(13), dp(13), dp(13))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF3E91FF.toInt()); setStroke(dp(2), 0x55FFFFFF) }
            alpha = 0.92f; elevation = dp(6).toFloat()
            contentDescription = getString(R.string.bubble_desc)
        }
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(8); y = resources.displayMetrics.heightPixels / 3
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        iv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true
                    if (moved) { lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt(); runCatching { wm.updateViewLayout(iv, lp) } }
                }
                MotionEvent.ACTION_UP -> if (!moved) showScan()
            }
            true
        }
        wm.addView(iv, lp)
        bubble = iv
    }

    private fun removeBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }
}

/** Full-screen dimmed overlay that outlines every price/number found on screen. */
@SuppressLint("ViewConstructor")
class ScanOverlayView(
    ctx: Context, private val cands: List<Candidate>, private val hint: String,
    private val onPick: (Candidate) -> Unit, private val onClose: () -> Unit,
) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val strong = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3E91FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    private val strongFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553E91FF }
    private val weak = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C20.toInt() }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 15f * d }
    private val closeRect = RectF()
    private val cardRect = RectF()

    override fun onDraw(c: Canvas) {
        c.drawColor(0xB3000000.toInt())
        val r = 8f * d
        for (k in cands.sortedBy { it.strong }) {
            val rf = RectF(k.rect).apply { inset(-4f * d, -2f * d) }
            if (k.strong) { c.drawRoundRect(rf, r, r, strongFill); c.drawRoundRect(rf, r, r, strong) } else c.drawRoundRect(rf, r, r, weak)
        }
        val top = 48f * d
        cardRect.set(16f * d, top, width - 16f * d, top + 56f * d)
        c.drawRoundRect(cardRect, 28f * d, 28f * d, card)
        c.drawText(hint, cardRect.left + 20f * d, cardRect.centerY() + 5f * d, txt)
        closeRect.set(cardRect.right - 52f * d, cardRect.top, cardRect.right, cardRect.bottom)
        c.drawText("✕", closeRect.centerX() - 7f * d, closeRect.centerY() + 6f * d, txt)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            if (closeRect.contains(e.x, e.y)) { onClose(); return true }
            val slop = 14f * d
            cands.filter { RectF(it.rect).apply { inset(-slop, -slop) }.contains(e.x, e.y) }
                .minByOrNull { it.rect.width().toLong() * it.rect.height() }?.let { onPick(it) }
        }
        return true
    }
}
