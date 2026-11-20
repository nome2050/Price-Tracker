package com.pricetracker.app.work

import android.content.Context
import androidx.work.*
import com.pricetracker.app.core.Compare
import com.pricetracker.app.core.PriceChecker
import com.pricetracker.app.core.Prefs
import java.util.concurrent.TimeUnit

class PriceCheckWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        runCatching { PriceChecker.checkAll(applicationContext) }
        return Result.success()
    }
}

class CompareWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("itemId", -1)
        if (id > 0) runCatching { Compare.run(applicationContext, id) }
        return Result.success()
    }
}

object Scheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(c: Context) {
        val minutes = Prefs.intervalMinutes(c).coerceAtLeast(15).toLong()
        val req = PeriodicWorkRequestBuilder<PriceCheckWorker>(minutes, TimeUnit.MINUTES).setConstraints(net).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("price-check", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun compare(c: Context, itemId: Long) {
        val req = OneTimeWorkRequestBuilder<CompareWorker>().setConstraints(net)
            .setInputData(workDataOf("itemId" to itemId)).build()
        WorkManager.getInstance(c).enqueueUniqueWork("compare-$itemId", ExistingWorkPolicy.REPLACE, req)
    }
}
