package com.pricetracker.app.core

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun intervalMinutes(c: Context) = sp(c).getInt("interval", 60)
    fun setInterval(c: Context, m: Int) = sp(c).edit().putInt("interval", m).apply()
    fun alerts(c: Context) = sp(c).getBoolean("alerts", true)
    fun setAlerts(c: Context, v: Boolean) = sp(c).edit().putBoolean("alerts", v).apply()
    fun autoCompare(c: Context) = sp(c).getBoolean("autoCompare", true)
    fun setAutoCompare(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoCompare", v).apply()
    fun groqKey(c: Context) = (sp(c).getString("groqKey", "") ?: "").ifBlank { Secrets.GROQ_KEY }
    fun setGroqKey(c: Context, k: String) = sp(c).edit().putString("groqKey", k.trim()).apply()
}
