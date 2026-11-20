package com.pricetracker.app.data

import android.content.Context
import androidx.room.Room
import com.pricetracker.app.core.Notifier
import com.pricetracker.app.core.Prefs
import kotlin.math.abs

object Graph {
    lateinit var app: Context
    lateinit var repo: Repository
    fun init(c: Context) {
        app = c.applicationContext
        val db = Room.databaseBuilder(app, AppDb::class.java, "tracker.db")// Never wipe user data on upgrade: when the schema changes, bump AppDb.version and add a Migration here.
            .fallbackToDestructiveMigrationOnDowngrade().build()
        repo = Repository(app, db.dao())
    }
}

class Repository(private val ctx: Context, val dao: PriceDao) {
    val items = dao.observeItems()
    val appItems = dao.observeAppItems()
    fun item(id: Long) = dao.observeItem(id)
    fun points(id: Long) = dao.observePoints(id)
    fun pointsSince(t: Long) = dao.observePointsSince(t)
    fun offers(id: Long) = dao.observeOffers(id)
    val allOffers = dao.observeAllOffers()

    suspend fun addItem(item: TrackedItem, price: Double?): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertItem(item.copy(currentPrice = price, lastChecked = now))
        if (price != null) dao.insertPoint(PricePoint(itemId = id, price = price, ts = now))
        return id
    }

    suspend fun recordPrice(itemId: Long, price: Double, currency: String? = null) {
        val item = dao.getItem(itemId) ?: return
        val now = System.currentTimeMillis()
        val last = dao.lastPoint(itemId)
        val changed = last == null || abs(last.price - price) > 0.0001
        if (changed || now - (last?.ts ?: 0) > 20 * 60_000L) dao.insertPoint(PricePoint(itemId = itemId, price = price, ts = now))
        val prev = item.currentPrice
        dao.updateItem(item.copy(currentPrice = price, currency = item.currency ?: currency, lastChecked = now, lastError = null))
        if (changed && prev != null && Prefs.alerts(ctx)) {
            val cur = item.currency ?: currency
            val target = item.targetPrice
            if (target != null && price <= target && prev > target) Notifier.targetHit(ctx, item, price, cur)
            else if (price < prev) Notifier.drop(ctx, item, prev, price, cur)
        }
    }

    suspend fun markError(id: Long, msg: String) {
        val item = dao.getItem(id) ?: return
        dao.updateItem(item.copy(lastError = msg, lastChecked = System.currentTimeMillis()))
    }

    suspend fun update(item: TrackedItem) = dao.updateItem(item)
    suspend fun get(id: Long) = dao.getItem(id)
    suspend fun delete(id: Long) = dao.deleteItem(id)
    suspend fun replaceOffers(id: Long, offers: List<Offer>) { dao.clearOffers(id); dao.insertOffers(offers) }
}
