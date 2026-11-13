package com.poltavalight.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.poltavalight.MainActivity
import com.poltavalight.R
import com.poltavalight.data.DailySchedule
import com.poltavalight.data.ScheduleRepository
import com.poltavalight.data.SlotStatus
import com.poltavalight.data.TimeSlot
import com.poltavalight.data.groupSlotsIntoBlocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Revolut Dark style Home Screen Widget for PoltavaLight.
 */
class LightAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.poltavalight.widget.ACTION_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, LightAppWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, LightAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
            // Also notify SplitAppWidgetProvider
            SplitAppWidgetProvider.updateAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repository = ScheduleRepository(context)
        val schedule = repository.getCachedSchedule()
        val queueLabel = repository.getSelectedQueueLabel()
        val lastCheck = repository.getLastCheckTime()

        if (schedule == null || schedule.slots.isEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.performCheck()
                    updateAllWidgets(context)
                } catch (e: Exception) {
                    // Ignore network error on initial update
                }
            }
        }

        for (appWidgetId in appWidgetIds) {
            try {
                updateWidgetView(context, appWidgetManager, appWidgetId, schedule, queueLabel, lastCheck)
            } catch (e: Exception) {
                // Prevent launcher crash
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WIDGET_REFRESH) {
            // Perform background refresh
            CoroutineScope(Dispatchers.IO).launch {
                val repo = ScheduleRepository(context)
                repo.performCheck()
                updateAllWidgets(context)
            }
        } else if (intent.action == ScheduleRepository.ACTION_SCHEDULE_UPDATED) {
            updateAllWidgets(context)
        }
    }

    private fun updateWidgetView(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        schedule: DailySchedule?,
        queueLabel: String,
        lastCheckTime: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_light_layout)

        // Queue title
        views.setTextViewText(R.id.widget_queue_title, queueLabel)
        views.setTextViewText(R.id.widget_updated_time, "Оновлено: $lastCheckTime")

        // Root click -> Open MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

        // Refresh button click -> ACTION_WIDGET_REFRESH
        val refreshIntent = Intent(context, LightAppWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

        // If no schedule available yet
        if (schedule == null || schedule.slots.isEmpty()) {
            views.setTextViewText(R.id.widget_status_text, "Невідомо")
            views.setTextColor(R.id.widget_status_text, Color.parseColor("#8E8E93"))
            views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_switching)
            views.setTextViewText(R.id.widget_timer_text, "Торкніться для оновлення")
            views.setTextViewText(R.id.widget_subtitle_text, "Дані графіка відсутні")
            views.setTextViewText(R.id.widget_next_events, "")
            manager.updateAppWidget(widgetId, views)
            return
        }

        // Calculate current status & time until next change
        val cal = Calendar.getInstance()
        val curHour = cal.get(Calendar.HOUR_OF_DAY)
        val curMin = cal.get(Calendar.MINUTE)
        val curTotalMin = curHour * 60 + curMin

        val currentSlot = schedule.slots.find { slot ->
            val slotStart = slot.startHour * 60 + slot.startMinute
            curTotalMin in slotStart until (slotStart + 30)
        } ?: schedule.slots.firstOrNull()

        val currentStatus = currentSlot?.status ?: SlotStatus.UNKNOWN

        // Status text & colors
        when (currentStatus) {
            SlotStatus.ON -> {
                views.setTextViewText(R.id.widget_status_text, "Світло є")
                views.setTextColor(R.id.widget_status_text, Color.parseColor("#00D180"))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_on)
                views.setTextViewText(R.id.widget_timer_text, "Світло є")
            }
            SlotStatus.OFF -> {
                views.setTextViewText(R.id.widget_status_text, "Світла немає")
                views.setTextColor(R.id.widget_status_text, Color.parseColor("#FF3B5C"))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_off)
                views.setTextViewText(R.id.widget_timer_text, "Світла немає")
            }
            SlotStatus.SWITCHING -> {
                views.setTextViewText(R.id.widget_status_text, "Перемикання")
                views.setTextColor(R.id.widget_status_text, Color.parseColor("#FFB800"))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_switching)
                views.setTextViewText(R.id.widget_timer_text, "Перемикання")
            }
            SlotStatus.UNKNOWN -> {
                views.setTextViewText(R.id.widget_status_text, "Невідомо")
                views.setTextColor(R.id.widget_status_text, Color.parseColor("#8E8E93"))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_switching)
                views.setTextViewText(R.id.widget_timer_text, "Невідомо")
            }
        }

        // Find next status transition
        val nextSlot = schedule.slots.find { slot ->
            val slotStart = slot.startHour * 60 + slot.startMinute
            slotStart > curTotalMin && slot.status != currentStatus
        }

        if (nextSlot != null) {
            val nextStart = nextSlot.startHour * 60 + nextSlot.startMinute
            val remainingMin = nextStart - curTotalMin
            val remH = remainingMin / 60
            val remM = remainingMin % 60
            val durationText = when {
                remH > 0 && remM > 0 -> "через $remH год $remM хв"
                remH > 0 -> "через $remH год"
                else -> "через $remM хв"
            }
            val nextTimeFormatted = String.format(Locale.US, "%02d:%02d", nextSlot.startHour, nextSlot.startMinute)
            views.setTextViewText(
                R.id.widget_subtitle_text,
                "Зміна о $nextTimeFormatted • $durationText"
            )
        } else {
            views.setTextViewText(
                R.id.widget_subtitle_text,
                "До кінця доби змін немає"
            )
        }

        // Find next 2 blocks for bottom row
        val blocks = groupSlotsIntoBlocks(schedule.slots)
        val futureBlocks = blocks.filter { (it.endHour * 60 + it.endMinute) > curTotalMin }
        val nextEventsStr = futureBlocks.take(2).joinToString(" • ") { b ->
            val statusLabel = when (b.status) {
                SlotStatus.ON -> "Світло"
                SlotStatus.OFF -> "Вимкнення"
                SlotStatus.SWITCHING -> "Перемикання"
                SlotStatus.UNKNOWN -> "—"
            }
            String.format(Locale.US, "%02d:%02d %s", b.startHour, b.startMinute, statusLabel)
        }
        if (nextEventsStr.isNotBlank()) {
            views.setTextViewText(R.id.widget_next_events, "Далі: $nextEventsStr")
        } else {
            views.setTextViewText(R.id.widget_next_events, "")
        }

        manager.updateAppWidget(widgetId, views)
    }
}

