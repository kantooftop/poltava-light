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
import com.poltavalight.data.SlotStatus

/**
 * Helper for creating and showing notifications in Revolut-inspired tone:
 * clean, concise, and helpful.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_EMERGENCY = "emergency_channel"
        const val CHANNEL_SCHEDULE = "schedule_channel"
        const val CHANNEL_REMINDER = "reminder_channel"
        const val CHANNEL_ONGOING = "ongoing_channel"

        private const val NOTIFICATION_ID_EMERGENCY = 1001
        private const val NOTIFICATION_ID_SCHEDULE = 1002
        private const val NOTIFICATION_ID_REMINDER = 1003
        private const val NOTIFICATION_ID_ONGOING = 1004
    }

    /**
     * Create all notification channels. Must be called at app startup.
     */
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

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

        // Reminder channel (15 mins prior)
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "Нагадування перед зміною",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Попередження за 15 хвилин до відключення чи перемикання"
            enableVibration(true)
        }

        // Ongoing status channel (low priority, silent)
        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING,
            "Поточний статус (у шторці)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Постійне сповіщення з таймером та статусом світла"
            setShowBadge(false)
        }

        manager.createNotificationChannels(
            listOf(emergencyChannel, scheduleChannel, reminderChannel, ongoingChannel)
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
        val text = "З $time ${event.reasonName}. ${event.note}".trim()

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
    fun showScheduleChangedNotification(queueName: String) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📋 Графік оновлено")
            .setContentText("Графік відключень ($queueName) оновився. Торкніться для перегляду.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent())
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_SCHEDULE, notification)
    }

    /**
     * Show smart 15-minute advance reminder before OFF or SWITCHING.
     */
    fun showOutageReminderNotification(
        startTime: String,
        status: SlotStatus,
        queueName: String
    ) {
        if (!hasNotificationPermission()) return

        val (title, text) = when (status) {
            SlotStatus.OFF -> Pair(
                "⚡ Відключення за 15 хв",
                "$queueName: відключення о $startTime. Збережіть роботу та зарядіть пристрої!"
            )
            SlotStatus.SWITCHING -> Pair(
                "⚠️ Перемикання за 15 хв",
                "$queueName: можливе відключення або перемикання о $startTime."
            )
            else -> Pair(
                "⚡ Зміна статусу за 15 хв",
                "$queueName: зміна графіка о $startTime."
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent())
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_REMINDER, notification)
    }

    /**
     * Optional live ongoing notification in status drawer with countdown.
     */
    fun updateOngoingNotification(queueName: String, statusText: String, subtitleText: String) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("$queueName • $statusText")
            .setContentText(subtitleText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createMainIntent())
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_ONGOING, notification)
    }

    fun cancelOngoingNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ONGOING)
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
