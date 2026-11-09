package com.poltavalight.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver that re-enqueues the periodic schedule check worker
 * after the device reboots.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted, enqueuing schedule check worker")
            ScheduleCheckWorker.enqueue(context)
        }
    }
}
