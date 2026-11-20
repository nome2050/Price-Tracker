package com.pricetracker.app

import android.app.Application
import com.pricetracker.app.core.Notifier
import com.pricetracker.app.data.Graph
import com.pricetracker.app.work.Scheduler

class PriceTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.createChannel(this)
        Scheduler.schedulePeriodic(this)
    }
}
