package com.poltavalight.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.poltavalight.MainActivity
import com.poltavalight.R
import com.poltavalight.data.EmergencyEvent

/**
 * Helper for creating and showing notifications.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_EMERGENCY = "emergency_channel"
        const val CHANNEL_SCHEDULE = "schedule_channel"
        const val CHANNEL_REMINDER = "reminder_channel"

        private const val NOTIFICATION_ID_EMERGENCY = 1001
        private const val NOTIFICATION_ID_SCHEDULE = 1002
        private const val NOTIFICATION_ID_REMINDER = 1003
    }

    /**
     * Create all notification channels. Must be called at app startup.
     */
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        // Emergency channel — max importance, alarm sound
        val emergencyChannel = NotificationChannel(
            CHANNEL_EMERGENCY,
            "Аварійні відключення (ГАВ/СГАВ)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Термінові сповіщення про аварійні відключення"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }

        // Schedule change channel
        val scheduleChannel = NotificationChannel(
            CHANNEL_SCHEDULE,
            "Зміна графіку",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Сповіщення про зміни в графіку відключень"
        }

        // Reminder channel
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "Нагадування",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Нагадування перед відключенням"
        }

        manager.createNotificationChannels(
            listOf(emergencyChannel, scheduleChannel, reminderChannel)
        )
    }

    /**
     * Show emergency notification (GAV/SGAV).
     */
    fun showEmergencyNotification(events: List<EmergencyEvent>) {
        if (!hasNotificationPermission()) return

        val gavEvents = events.filter { it.typeId == 1 || it.typeId == 2 }
        if (gavEvents.isEmpty()) return

        val event = gavEvents.first()
        val typeLabel = when (event.typeId) {
            1 -> "ГАВ"
            2 -> "СГАВ"
            else -> "Аварійне"
        }

        val title = "🚨 $typeLabel — Аварійне відключення!"
        val time = String.format("%d:%02d", event.beginHour, event.beginMinute)
        val text = "З $time ${event.reasonName}. ${event.note}"

        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent())
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_EMERGENCY, notification)
    }

    /**
     * Show schedule change notification.
     */
    fun showScheduleChangedNotification() {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📋 Графік змінився")
            .setContentText("Графік відключень для черги 6.1 було оновлено. Натисніть, щоб переглянути.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent())
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_SCHEDULE, notification)
    }

    /**
     * Show a reminder that an outage is coming soon.
     */
    fun showOutageReminderNotification(startTime: String) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚡ Скоро відключення")
            .setContentText("Черга 6.1: відключення о $startTime. Зарядіть пристрої!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent())
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_REMINDER, notification)
    }

    private fun createMainIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
