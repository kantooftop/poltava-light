package com.poltavalight.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.poltavalight.MainActivity
import com.poltavalight.R
import com.poltavalight.data.DailySchedule
import com.poltavalight.data.ScheduleBlock
import com.poltavalight.data.ScheduleRepository
import com.poltavalight.data.SlotStatus
import com.poltavalight.data.groupSlotsIntoBlocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Split (Dual-band / Capsule) Widget for PoltavaLight.
 * Matches the green/red two-tier style with lightbulb icons and queue label.
 */
class SplitAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SPLIT_REFRESH = "com.poltavalight.widget.ACTION_SPLIT_REFRESH"
        private const val TAG = "SplitWidgetProvider"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SplitAppWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, SplitAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repository = ScheduleRepository(context)
        val schedule = repository.getCachedSchedule()
        val queueNumber = repository.getSelectedQueueNumber()

        // If no cached schedule, fetch in background
        if (schedule == null || schedule.slots.isEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.performCheck(queueNumber)
                    updateAllWidgets(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching schedule on update", e)
                }
            }
        }

        for (appWidgetId in appWidgetIds) {
            try {
                updateWidgetView(context, appWidgetManager, appWidgetId, schedule, queueNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating split widget $appWidgetId", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_SPLIT_REFRESH) {
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
        queueNumber: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_split_layout)

        // Queue number in top left corner (e.g. "4.2")
        views.setTextViewText(R.id.widget_split_queue, queueNumber)

        // Clicking root opens MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            widgetId + 20000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_split_root, openAppPendingIntent)

        if (schedule == null || schedule.slots.isEmpty()) {
            // Placeholder when data is not yet downloaded
            views.setTextViewText(R.id.widget_split_top_time, "Оновлення...")
            views.setInt(R.id.widget_split_top_container, "setBackgroundResource", R.drawable.widget_split_top_green)
            views.setImageViewResource(R.id.widget_split_top_icon, R.drawable.ic_widget_bulb_on)

            views.setTextViewText(R.id.widget_split_bottom_time, "Торкніться для оновлення")
            views.setInt(R.id.widget_split_bottom_container, "setBackgroundResource", R.drawable.widget_split_bottom_red)
            views.setImageViewResource(R.id.widget_split_bottom_icon, R.drawable.ic_widget_bulb_off)

            manager.updateAppWidget(widgetId, views)
            return
        }

        val cal = Calendar.getInstance()
        val curHour = cal.get(Calendar.HOUR_OF_DAY)
        val curMin = cal.get(Calendar.MINUTE)
        val curTotalMin = curHour * 60 + curMin

        val blocks = groupSlotsIntoBlocks(schedule.slots)
        if (blocks.isEmpty()) {
            manager.updateAppWidget(widgetId, views)
            return
        }

        // Current block (by time)
        val currentBlock = blocks.find { block ->
            val start = block.startHour * 60 + block.startMinute
            val end = block.endHour * 60 + block.endMinute
            curTotalMin in start until end
        } ?: blocks.first()

        // Next block after current
        val currentEndMin = currentBlock.endHour * 60 + currentBlock.endMinute
        val nextBlock = blocks.find { block ->
            val start = block.startHour * 60 + block.startMinute
            start >= currentEndMin
        }

        // 1. Configure Top Half (Current Period)
        val topTimeStr = String.format(
            Locale.US,
            "%02d:%02d - %02d:%02d",
            currentBlock.startHour,
            currentBlock.startMinute,
            currentBlock.endHour,
            currentBlock.endMinute
        )
        views.setTextViewText(R.id.widget_split_top_time, topTimeStr)

        when (currentBlock.status) {
            SlotStatus.ON -> {
                views.setInt(R.id.widget_split_top_container, "setBackgroundResource", R.drawable.widget_split_top_green)
                views.setImageViewResource(R.id.widget_split_top_icon, R.drawable.ic_widget_bulb_on)
            }
            SlotStatus.OFF -> {
                views.setInt(R.id.widget_split_top_container, "setBackgroundResource", R.drawable.widget_split_top_red)
                views.setImageViewResource(R.id.widget_split_top_icon, R.drawable.ic_widget_bulb_off)
            }
            SlotStatus.SWITCHING -> {
                views.setInt(R.id.widget_split_top_container, "setBackgroundResource", R.drawable.widget_split_top_yellow)
                views.setImageViewResource(R.id.widget_split_top_icon, R.drawable.ic_widget_bulb_on)
            }
            SlotStatus.UNKNOWN -> {
                views.setInt(R.id.widget_split_top_container, "setBackgroundResource", R.drawable.widget_split_top_gray)
                views.setImageViewResource(R.id.widget_split_top_icon, R.drawable.ic_widget_bulb_on)
            }
        }

        // 2. Configure Bottom Half (Next Period)
        if (nextBlock != null) {
            val bottomTimeStr = String.format(
                Locale.US,
                "%02d:%02d - %02d:%02d",
                nextBlock.startHour,
                nextBlock.startMinute,
                nextBlock.endHour,
                nextBlock.endMinute
            )
            views.setTextViewText(R.id.widget_split_bottom_time, bottomTimeStr)

            when (nextBlock.status) {
                SlotStatus.ON -> {
                    views.setInt(R.id.widget_split_bottom_container, "setBackgroundResource", R.drawable.widget_split_bottom_green)
                    views.setImageViewResource(R.id.widget_split_bottom_icon, R.drawable.ic_widget_bulb_on)
                }
                SlotStatus.OFF -> {
                    views.setInt(R.id.widget_split_bottom_container, "setBackgroundResource", R.drawable.widget_split_bottom_red)
                    views.setImageViewResource(R.id.widget_split_bottom_icon, R.drawable.ic_widget_bulb_off)
                }
                SlotStatus.SWITCHING -> {
                    views.setInt(R.id.widget_split_bottom_container, "setBackgroundResource", R.drawable.widget_split_bottom_yellow)
                    views.setImageViewResource(R.id.widget_split_bottom_icon, R.drawable.ic_widget_bulb_on)
                }
                SlotStatus.UNKNOWN -> {
                    views.setInt(R.id.widget_split_bottom_container, "setBackgroundResource", R.drawable.widget_split_bottom_gray)
                    views.setImageViewResource(R.id.widget_split_bottom_icon, R.drawable.ic_widget_bulb_on)
                }
            }
        } else {
            // End of day
            views.setTextViewText(R.id.widget_split_bottom_time, "До кінця доби")
            views.setInt(R.id.widget_split_bottom_container, "setBackgroundResource", R.drawable.widget_split_bottom_gray)
            views.setImageViewResource(R.id.widget_split_bottom_icon, R.drawable.ic_widget_bulb_on)
        }

        manager.updateAppWidget(widgetId, views)
    }
}
