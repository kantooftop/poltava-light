package com.poltavalight.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository that combines API fetching, parsing, and local state tracking.
 * Uses SharedPreferences to store the last known schedule hash,
 * queue settings, and cached data for widgets.
 */
class ScheduleRepository(private val context: Context) {

    private val api = PoeApiService()
    private val parser = ScheduleParser()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("poltava_light_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_SCHEDULE_HASH = "last_schedule_hash"
        private const val KEY_LAST_EMERGENCY_IDS = "last_emergency_ids"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_CACHED_SCHEDULE_JSON = "cached_schedule_slots"
        private const val KEY_CACHED_SCHEDULE_DATE = "cached_schedule_date"
        private const val KEY_CACHED_SCHEDULE_SUMMARY = "cached_schedule_summary"
        private const val KEY_SELECTED_QUEUE = "selected_queue"
        private const val KEY_SELECTED_QUEUE_LABEL = "selected_queue_label"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_SAVED_QUEUES = "saved_queues_list"

        const val ACTION_SCHEDULE_UPDATED = "com.poltavalight.action.SCHEDULE_UPDATED"
    }

    /**
     * Get the active queue number (e.g. "6.1").
     */
    fun getSelectedQueueNumber(): String {
        return prefs.getString(KEY_SELECTED_QUEUE, "6.1") ?: "6.1"
    }

    /**
     * Get the active queue label (e.g. "Дім" or "Черга 6.1").
     */
    fun getSelectedQueueLabel(): String {
        return prefs.getString(KEY_SELECTED_QUEUE_LABEL, "Черга 6.1") ?: "Черга 6.1"
    }

    fun setSelectedQueue(queueNumber: String, label: String) {
        prefs.edit()
            .putString(KEY_SELECTED_QUEUE, queueNumber)
            .putString(KEY_SELECTED_QUEUE_LABEL, label)
            .apply()
    }

    /**
     * Are 15-minute outage reminders enabled?
     */
    fun isNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    /**
     * Get list of saved queues for quick switching.
     */
    fun getSavedQueues(): List<SavedQueue> {
        val raw = prefs.getString(KEY_SAVED_QUEUES, null)
        if (raw.isNullOrBlank()) {
            return listOf(
                SavedQueue("1", "Дім", "6.1"),
                SavedQueue("2", "Батьки", "2.2"),
                SavedQueue("3", "Робота", "3.1")
            )
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SavedQueue>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedQueue(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        queueNumber = obj.getString("queueNumber")
                    )
                )
            }
            list
        } catch (e: Exception) {
            listOf(SavedQueue("1", "Дім", "6.1"))
        }
    }

    fun saveQueues(list: List<SavedQueue>) {
        val arr = JSONArray()
        for (q in list) {
            val obj = JSONObject()
            obj.put("id", q.id)
            obj.put("label", q.label)
            obj.put("queueNumber", q.queueNumber)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_QUEUES, arr.toString()).apply()
    }

    /**
     * Perform a full check: fetch schedule + emergencies, detect changes.
     */
    fun performCheck(queueNumber: String = getSelectedQueueNumber()): CheckResult {
        return try {
            val gpvHtml = api.fetchGpvHtml()
            val emergencyJson = api.fetchEmergencyJson()

            val schedule = parser.parseGpvHtml(gpvHtml, queueNumber)
            val tomorrowSchedule = parser.parseTomorrowGpvHtml(gpvHtml, queueNumber)
            val emergencies = parser.parseEmergencyJson(emergencyJson)

            val checkTime = currentTimeFormatted()
            prefs.edit()
                .putString(KEY_LAST_CHECK_TIME, checkTime)
                .apply()

            // Cache today's schedule for offline/widget use
            cacheSchedule(schedule)

            // Broadcast update to widgets and components
            notifyDataUpdated()

            CheckResult(
                schedule = schedule,
                tomorrowSchedule = tomorrowSchedule,
                emergencies = emergencies
            )
        } catch (e: Exception) {
            CheckResult(
                schedule = getCachedSchedule(),
                emergencies = emptyList(),
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Check if the schedule has changed compared to last known state.
     */
    fun hasScheduleChanged(newHash: String): Boolean {
        val lastHash = prefs.getString(KEY_LAST_SCHEDULE_HASH, "") ?: ""
        return lastHash.isNotEmpty() && lastHash != newHash
    }

    /**
     * Check if there's a new emergency event we haven't notified about.
     */
    fun hasNewEmergency(emergencies: List<EmergencyEvent>): Boolean {
        val activeEmergencies = emergencies.filter { it.typeId == 1 || it.typeId == 2 }
        if (activeEmergencies.isEmpty()) return false

        val newIds = activeEmergencies.map { "${it.typeId}_${it.date}_${it.beginHour}:${it.beginMinute}" }
            .toSet()
            .joinToString(",")

        val lastIds = prefs.getString(KEY_LAST_EMERGENCY_IDS, "") ?: ""
        return newIds != lastIds
    }

    /**
     * Save the current state so we can detect future changes.
     */
    fun saveCurrentState(scheduleHash: String, emergencies: List<EmergencyEvent>) {
        val emergencyIds = emergencies
            .filter { it.typeId == 1 || it.typeId == 2 }
            .map { "${it.typeId}_${it.date}_${it.beginHour}:${it.beginMinute}" }
            .toSet()
            .joinToString(",")

        prefs.edit()
            .putString(KEY_LAST_SCHEDULE_HASH, scheduleHash)
            .putString(KEY_LAST_EMERGENCY_IDS, emergencyIds)
            .apply()
    }

    fun getLastCheckTime(): String {
        return prefs.getString(KEY_LAST_CHECK_TIME, "—") ?: "—"
    }

    private fun currentTimeFormatted(): String {
        val sdf = SimpleDateFormat("HH:mm dd.MM", Locale("uk", "UA"))
        return sdf.format(Date())
    }

    private fun cacheSchedule(schedule: DailySchedule) {
        try {
            val arr = JSONArray()
            for (slot in schedule.slots) {
                val obj = JSONObject()
                obj.put("h", slot.startHour)
                obj.put("m", slot.startMinute)
                obj.put("s", slot.status.name)
                obj.put("t", slot.title)
                arr.put(obj)
            }
            prefs.edit()
                .putString(KEY_CACHED_SCHEDULE_JSON, arr.toString())
                .putString(KEY_CACHED_SCHEDULE_DATE, schedule.date)
                .putString(KEY_CACHED_SCHEDULE_SUMMARY, schedule.summaryText)
                .apply()
        } catch (e: Exception) {
            // Ignore cache error
        }
    }

    fun getCachedSchedule(): DailySchedule? {
        val raw = prefs.getString(KEY_CACHED_SCHEDULE_JSON, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            val slots = mutableListOf<TimeSlot>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                slots.add(
                    TimeSlot(
                        startHour = obj.getInt("h"),
                        startMinute = obj.getInt("m"),
                        status = SlotStatus.valueOf(obj.getString("s")),
                        title = obj.optString("t", "")
                    )
                )
            }
            val date = prefs.getString(KEY_CACHED_SCHEDULE_DATE, "") ?: ""
            val summary = prefs.getString(KEY_CACHED_SCHEDULE_SUMMARY, "") ?: ""
            DailySchedule(
                date = date,
                slots = slots,
                summaryText = summary
            )
        } catch (e: Exception) {
            null
        }
    }

    fun notifyDataUpdated() {
        try {
            val intent = Intent(ACTION_SCHEDULE_UPDATED)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // Ignore broadcast failure
        }
    }
}
