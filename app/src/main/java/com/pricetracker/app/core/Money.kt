package com.pricetracker.app.core

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    fun format(v: Double, cur: String?): String {
        try {
            if (cur != null) {
                val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
                nf.currency = Currency.getInstance(cur)
                return nf.format(v)
            }
        } catch (_: Exception) {}
        return String.format(Locale.getDefault(), "%,.2f", v)
    }
}
