package com.poltavalight.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.poltavalight.data.ScheduleRepository
import com.poltavalight.data.SlotStatus
import com.poltavalight.data.TimeSlot
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks the POE website
 * for schedule changes and emergency outages.
 *
 * Runs every 15 minutes (WorkManager minimum interval).
 */
class ScheduleCheckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val WORK_NAME = "schedule_check_worker"
        private const val TAG = "ScheduleCheckWorker"

        /**
         * Enqueue the periodic worker. Call this at app startup and after boot.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Periodic schedule check worker enqueued")
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting schedule check...")

        val repository = ScheduleRepository(applicationContext)
        val notificationHelper = NotificationHelper(applicationContext)
        val selectedQueue = repository.getSelectedQueueNumber()
        val queueLabel = repository.getSelectedQueueLabel()

        val result = repository.performCheck(selectedQueue)

        if (result.error != null) {
            Log.w(TAG, "Check failed: ${result.error}")
            return Result.retry()
        }

        // Check for emergency outages (GAV/SGAV)
        if (repository.hasNewEmergency(result.emergencies)) {
            Log.i(TAG, "New emergency detected!")
            notificationHelper.showEmergencyNotification(result.emergencies)
        }

        // Check if schedule changed
        val schedule = result.schedule
        if (schedule != null && schedule.slots.isNotEmpty()) {
            if (repository.hasScheduleChanged(schedule.rawHash)) {
                Log.i(TAG, "Schedule changed!")
                notificationHelper.showScheduleChangedNotification(queueLabel)
            }

            // Check if outage or switching is coming in the next 15-30 minutes
            if (repository.isNotificationsEnabled()) {
                checkUpcomingOutageOrSwitching(schedule.slots, notificationHelper, queueLabel)
            }

            // Save current state
            repository.saveCurrentState(schedule.rawHash, result.emergencies)
        }

        Log.d(TAG, "Schedule check completed successfully")
        return Result.success()
    }

    /**
     * Check if an outage or switching starts in the next ~15-30 minutes and send a reminder.
     */
    private fun checkUpcomingOutageOrSwitching(
        slots: List<TimeSlot>,
        notificationHelper: NotificationHelper,
        queueLabel: String
    ) {
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        // Scan upcoming slots within 10 to 30 minutes
        for (slot in slots) {
            if (slot.status != SlotStatus.OFF && slot.status != SlotStatus.SWITCHING) continue

            val slotTotalMinutes = slot.startHour * 60 + slot.startMinute
            val diff = slotTotalMinutes - currentTotalMinutes

            // Trigger when exactly 10 to 25 minutes prior
            if (diff in 10..25) {
                // Find previous slot to verify this is an actual transition
                val prevSlotMinutes = slotTotalMinutes - 30
                val prevSlot = slots.find {
                    it.startHour * 60 + it.startMinute == prevSlotMinutes
                }

                // If previous slot was ON, then this is an upcoming outage or switching!
                if (prevSlot == null || prevSlot.status == SlotStatus.ON) {
                    val timeStr = String.format("%02d:%02d", slot.startHour, slot.startMinute)

                    val prefs = applicationContext.getSharedPreferences(
                        "poltava_light_prefs", Context.MODE_PRIVATE
                    )
                    val lastReminder = prefs.getString("last_reminder_slot", "") ?: ""
                    val reminderKey = "${cal.get(Calendar.DAY_OF_YEAR)}_${slot.startHour}_${slot.startMinute}_${slot.status.name}"

                    if (lastReminder != reminderKey) {
                        notificationHelper.showOutageReminderNotification(timeStr, slot.status, queueLabel)
                        prefs.edit().putString("last_reminder_slot", reminderKey).apply()
                    }
                }
            }
        }
    }
}
