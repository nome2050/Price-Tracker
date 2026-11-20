package com.pricetracker.app.core

import kotlin.math.max

data class ParsedPrice(val value: Double, val currency: String?, val raw: String)

object PriceParser {
    private val SYMBOLS = linkedMapOf(
        "US$" to "USD", "HK$" to "HKD", "NZ$" to "NZD", "R$" to "BRL", "A$" to "AUD", "C$" to "CAD", "S$" to "SGD",
        "Rs." to "INR", "Rs" to "INR", "₹" to "INR", "ر.س" to "SAR", "SR" to "SAR", "ريال" to "SAR", "د.إ" to "AED",
        "€" to "EUR", "£" to "GBP", "￥" to "JPY", "¥" to "JPY", "₩" to "KRW", "₽" to "RUB", "₺" to "TRY",
        "₫" to "VND", "₪" to "ILS", "₦" to "NGN", "฿" to "THB", "zł" to "PLN", "$" to "USD",
    )
    private val ISO = Regex("\\b(USD|EUR|GBP|INR|JPY|CNY|KRW|AUD|CAD|CHF|SEK|NOK|DKK|PLN|BRL|MXN|AED|SAR|SGD|HKD|NZD|ZAR|TRY|RUB|THB|IDR|MYR|PHP|VND|CZK|HUF|ILS|KWD|QAR|BHD|OMR|EGP)\\b")
    private val NUM = Regex("""\d{1,3}(?:[ ,.  ']\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?""")
    private val NUMERIC_ONLY = Regex("""^[^\d]{0,12}\d[\d.,\s ]*[^\d]{0,12}$""")
    private val DECIMAL = Regex("""\d[.,]\d{2}(?!\d)""")

    fun detectCurrency(text: String): String? {
        ISO.find(text)?.let { return it.value }
        for ((s, c) in SYMBOLS) if (text.contains(s)) return c
        return null
    }

    fun findAll(text: String): List<ParsedPrice> {
        val cur = detectCurrency(text)
        return NUM.findAll(text).mapNotNull { m -> toDouble(m.value)?.let { ParsedPrice(it, cur, m.value) } }.toList()
    }

    fun parse(text: String): ParsedPrice? = findAll(text).firstOrNull()

    /** 0 = not a number, 1 = plain number, 2 = looks like a price */
    fun classify(text: String): Int {
        val t = text.trim()
        if (t.isEmpty() || t.length > 80 || !t.any { it.isDigit() }) return 0
        val hasCur = detectCurrency(t) != null
        if (hasCur && t.length <= 40) return 2
        if (t.length <= 24 && NUMERIC_ONLY.matches(t)) return if (DECIMAL.containsMatchIn(t) || NUM.find(t)?.value?.let { it.length > 3 && (it.contains(',') || it.contains('.')) } == true) 2 else 1
        return 0
    }

    fun toDouble(raw: String): Double? {
        var s = raw.replace(Regex("[\\s\\u00A0\\u202F']"), "")
        val dot = s.lastIndexOf('.')
        val comma = s.lastIndexOf(',')
        if (dot >= 0 && comma >= 0) {
            val dec = max(dot, comma)
            s = s.substring(0, dec).replace(".", "").replace(",", "") + "." + s.substring(dec + 1)
        } else if (dot >= 0 || comma >= 0) {
            val sep = if (dot >= 0) '.' else ','
            val count = s.count { it == sep }
            val after = s.length - s.lastIndexOf(sep) - 1
            s = if (count > 1 || (after == 3 && s.substringBefore(sep) != "0")) s.replace(sep.toString(), "") else s.replace(sep, '.')
        }
        return s.toDoubleOrNull()
    }
}
