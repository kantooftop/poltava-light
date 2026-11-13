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
 * Automatically caches all 12 queues of Poltava region so the app works
 * immediately offline, on app resume, or when VPN limits access.
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
        private const val KEY_CACHED_SCHEDULE_DATE = "cached_schedule_date"
        private const val KEY_CACHED_SCHEDULE_SUMMARY = "cached_schedule_summary"
        private const val KEY_CACHED_EMERGENCIES_JSON = "cached_emergencies_json"
        private const val KEY_SELECTED_QUEUE = "selected_queue"
        private const val KEY_SELECTED_QUEUE_LABEL = "selected_queue_label"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

        const val ACTION_SCHEDULE_UPDATED = "com.poltavalight.action.SCHEDULE_UPDATED"

        /**
         * All 12 queues (sub-queues) of Poltavaoblenergo across the Poltava region.
         */
        val ALL_POLTAVA_QUEUES = listOf(
            "1.1", "1.2",
            "2.1", "2.2",
            "3.1", "3.2",
            "4.1", "4.2",
            "5.1", "5.2",
            "6.1", "6.2"
        )
    }

    /**
     * Get the active queue number (e.g. "4.2").
     */
    fun getSelectedQueueNumber(): String {
        return prefs.getString(KEY_SELECTED_QUEUE, "4.2") ?: "4.2"
    }

    /**
     * Get the active queue label (e.g. "Черга 4.2").
     */
    fun getSelectedQueueLabel(): String {
        val q = getSelectedQueueNumber()
        return "Черга $q"
    }

    fun setSelectedQueue(queueNumber: String, label: String = "Черга $queueNumber") {
        prefs.edit()
            .putString(KEY_SELECTED_QUEUE, queueNumber)
            .putString(KEY_SELECTED_QUEUE_LABEL, "Черга $queueNumber")
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
     * Get list of all official queues.
     */
    fun getSavedQueues(): List<SavedQueue> {
        return ALL_POLTAVA_QUEUES.map { q ->
            SavedQueue(id = q, label = "Черга $q", queueNumber = q)
        }
    }

    /**
     * Perform a full check: fetch schedule + emergencies, cache all queues, detect changes.
     */
    fun performCheck(queueNumber: String = getSelectedQueueNumber()): CheckResult {
        return try {
            val gpvHtml = api.fetchGpvHtml()
            val emergencyJson = api.fetchEmergencyJson()

            // Parse all 12 queues at once
            val allQueues = parser.parseAllQueuesFromHtml(gpvHtml)
            val schedule = allQueues[queueNumber] ?: parser.parseGpvHtml(gpvHtml, queueNumber)
            val tomorrowSchedule = parser.parseTomorrowGpvHtml(gpvHtml, queueNumber)
            val emergencies = parser.parseEmergencyJson(emergencyJson)

            val checkTime = currentTimeFormatted()
            prefs.edit()
                .putString(KEY_LAST_CHECK_TIME, checkTime)
                .apply()

            // Cache all 12 queues for instant offline and cross-queue switching
            cacheAllQueues(allQueues, schedule.date, schedule.summaryText)
            cacheEmergencies(emergencies)

            // Broadcast update to widgets and components
            notifyDataUpdated()

            CheckResult(
                schedule = schedule,
                tomorrowSchedule = tomorrowSchedule,
                emergencies = emergencies
            )
        } catch (e: Exception) {
            // On failure (e.g. VPN geo-block or offline), provide the cached data for selected queue
            val cachedSchedule = getCachedSchedule(queueNumber)
            val cachedEmergencies = getCachedEmergencies()

            CheckResult(
                schedule = cachedSchedule,
                emergencies = cachedEmergencies,
                error = e.localizedMessage ?: "Помилка зв'язку з сайтом обленерго"
            )
        }
    }

    /**
     * Get instantly cached check result for a queue without triggering network.
     */
    fun getCachedCheckResult(queueNumber: String = getSelectedQueueNumber()): CheckResult {
        return CheckResult(
            schedule = getCachedSchedule(queueNumber),
            emergencies = getCachedEmergencies()
        )
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

    /**
     * Cache all parsed queues into SharedPreferences for offline and quick switching.
     */
    private fun cacheAllQueues(
        schedules: Map<String, DailySchedule>,
        dateText: String,
        summaryText: String
    ) {
        try {
            val editor = prefs.edit()
            editor.putString(KEY_CACHED_SCHEDULE_DATE, dateText)
            editor.putString(KEY_CACHED_SCHEDULE_SUMMARY, summaryText)

            for ((queueNum, schedule) in schedules) {
                val arr = JSONArray()
                for (slot in schedule.slots) {
                    val obj = JSONObject()
                    obj.put("h", slot.startHour)
                    obj.put("m", slot.startMinute)
                    obj.put("s", slot.status.name)
                    obj.put("t", slot.title)
                    arr.put(obj)
                }
                editor.putString("cached_slots_$queueNum", arr.toString())
            }
            editor.apply()
        } catch (e: Exception) {
            // Ignore cache error
        }
    }

    /**
     * Get cached schedule for a specific queue number.
     */
    fun getCachedSchedule(queueNumber: String = getSelectedQueueNumber()): DailySchedule? {
        val raw = prefs.getString("cached_slots_$queueNumber", null)
            ?: prefs.getString("cached_schedule_slots", null)
            ?: return null

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

    private fun cacheEmergencies(emergencies: List<EmergencyEvent>) {
        try {
            val arr = JSONArray()
            for (ev in emergencies) {
                val obj = JSONObject()
                obj.put("date", ev.date)
                obj.put("bh", ev.beginHour)
                obj.put("bm", ev.beginMinute)
                obj.put("eh", ev.endHour)
                obj.put("em", ev.endMinute)
                obj.put("tn", ev.typeName)
                obj.put("ti", ev.typeId)
                obj.put("rn", ev.reasonName)
                obj.put("note", ev.note)
                obj.put("vol", ev.volume)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_CACHED_EMERGENCIES_JSON, arr.toString()).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getCachedEmergencies(): List<EmergencyEvent> {
        val raw = prefs.getString(KEY_CACHED_EMERGENCIES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<EmergencyEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    EmergencyEvent(
                        date = obj.optString("date", ""),
                        beginHour = obj.optInt("bh", 0),
                        beginMinute = obj.optInt("bm", 0),
                        endHour = obj.optInt("eh", 0),
                        endMinute = obj.optInt("em", 0),
                        typeName = obj.optString("tn", ""),
                        typeId = obj.optInt("ti", 0),
                        reasonName = obj.optString("rn", ""),
                        note = obj.optString("note", ""),
                        createdDate = "",
                        volume = obj.optInt("vol", 0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
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
