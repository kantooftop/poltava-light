package com.poltavalight.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.poltavalight.data.ScheduleRepository
import com.poltavalight.data.SlotStatus
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
                15, TimeUnit.MINUTES  // Minimum interval for WorkManager
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
        val result = repository.performCheck()

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
                notificationHelper.showScheduleChangedNotification()
            }

            // Check if outage is coming in the next 30 minutes
            checkUpcomingOutage(schedule.slots, notificationHelper)

            // Save current state
            repository.saveCurrentState(schedule.rawHash, result.emergencies)
        }

        Log.d(TAG, "Schedule check completed successfully")
        return Result.success()
    }

    /**
     * Check if an outage starts in the next ~30 minutes and send a reminder.
     */
    private fun checkUpcomingOutage(
        slots: List<com.poltavalight.data.TimeSlot>,
        notificationHelper: NotificationHelper
    ) {
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        // Find the next OFF slot within the next 30-45 minutes
        for (slot in slots) {
            if (slot.status != SlotStatus.OFF) continue

            val slotTotalMinutes = slot.startHour * 60 + slot.startMinute
            val diff = slotTotalMinutes - currentTotalMinutes

            if (diff in 15..45) {
                // Check if previous slot is ON (transition from power to no power)
                val prevSlotMinutes = slotTotalMinutes - 30
                val prevSlot = slots.find {
                    it.startHour * 60 + it.startMinute == prevSlotMinutes
                }

                if (prevSlot?.status == SlotStatus.ON || prevSlot?.status == SlotStatus.SWITCHING) {
                    val timeStr = String.format("%d:%02d", slot.startHour, slot.startMinute)

                    // Check if we already reminded about this slot today
                    val prefs = applicationContext.getSharedPreferences(
                        "poltava_light_prefs", Context.MODE_PRIVATE
                    )
                    val lastReminder = prefs.getString("last_reminder", "") ?: ""
                    val reminderKey = "${cal.get(Calendar.DAY_OF_YEAR)}_${slot.startHour}_${slot.startMinute}"

                    if (lastReminder != reminderKey) {
                        notificationHelper.showOutageReminderNotification(timeStr)
                        prefs.edit().putString("last_reminder", reminderKey).apply()
                    }
                }
            }
        }
    }
}
