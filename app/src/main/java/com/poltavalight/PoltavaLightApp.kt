package com.poltavalight

import android.app.Application
import com.poltavalight.worker.NotificationHelper
import com.poltavalight.worker.ScheduleCheckWorker

/**
 * Application class — initializes notification channels
 * and enqueues the periodic background worker on app start.
 */
class PoltavaLightApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create notification channels
        NotificationHelper(this).createChannels()

        // Enqueue periodic schedule check
        ScheduleCheckWorker.enqueue(this)
    }
}

