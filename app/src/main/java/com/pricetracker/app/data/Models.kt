package com.pricetracker.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "items")
data class TrackedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** WEB, APP or MANUAL */
    val source: String,
    val url: String? = null,
    val selector: String? = null,
    val packageName: String? = null,
    val appLabel: String? = null,
    val viewId: String? = null,
    val contextText: String? = null,
    val currency: String? = null,
    val currentPrice: Double? = null,
    val targetPrice: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastChecked: Long? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "prices",
    foreignKeys = [ForeignKey(TrackedItem::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class PricePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val price: Double,
    val ts: Long,
)

/** A price found for the same product on another store (Product 360). */
@Entity(
    tableName = "offers",
    foreignKeys = [ForeignKey(TrackedItem::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class Offer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val store: String,
    val title: String,
    val price: Double,
    val currency: String?,
    val url: String,
    val ts: Long,
)

@Dao
interface PriceDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC") fun observeItems(): Flow<List<TrackedItem>>
    @Query("SELECT * FROM items WHERE id=:id") fun observeItem(id: Long): Flow<TrackedItem?>
    @Query("SELECT * FROM items WHERE id=:id") suspend fun getItem(id: Long): TrackedItem?
    @Query("SELECT * FROM items WHERE source='WEB' AND url IS NOT NULL") suspend fun webItems(): List<TrackedItem>
    @Query("SELECT * FROM items WHERE source='APP'") fun observeAppItems(): Flow<List<TrackedItem>>
    @Insert suspend fun insertItem(i: TrackedItem): Long
    @Update suspend fun updateItem(i: TrackedItem)
    @Query("DELETE FROM items WHERE id=:id") suspend fun deleteItem(id: Long)

    @Insert suspend fun insertPoint(p: PricePoint)
    @Query("SELECT * FROM prices WHERE itemId=:id ORDER BY ts ASC") fun observePoints(id: Long): Flow<List<PricePoint>>
    @Query("SELECT * FROM prices WHERE ts>=:since ORDER BY ts ASC") fun observePointsSince(since: Long): Flow<List<PricePoint>>
    @Query("SELECT * FROM prices WHERE itemId=:id ORDER BY ts DESC LIMIT 1") suspend fun lastPoint(id: Long): PricePoint?

    @Query("SELECT * FROM offers WHERE itemId=:id ORDER BY price ASC") fun observeOffers(id: Long): Flow<List<Offer>>
    @Query("SELECT * FROM offers") fun observeAllOffers(): Flow<List<Offer>>
    @Query("DELETE FROM offers WHERE itemId=:id") suspend fun clearOffers(id: Long)
    @Insert suspend fun insertOffers(o: List<Offer>)
}

@Database(entities = [TrackedItem::class, PricePoint::class, Offer::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): PriceDao
}
