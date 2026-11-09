package com.poltavalight.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository that combines API fetching, parsing, and local state tracking.
 * Uses SharedPreferences to store the last known schedule hash
 * and emergency state for change detection.
 */
class ScheduleRepository(context: Context) {

    private val api = PoeApiService()
    private val parser = ScheduleParser()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("poltava_light_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_SCHEDULE_HASH = "last_schedule_hash"
        private const val KEY_LAST_EMERGENCY_IDS = "last_emergency_ids"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_CACHED_SCHEDULE_JSON = "cached_schedule_slots"
        private const val KEY_CACHED_EMERGENCIES = "cached_emergencies"
    }

    /**
     * Perform a full check: fetch schedule + emergencies, detect changes.
     * Returns CheckResult with the data and whether anything changed.
     */
    fun performCheck(): CheckResult {
        return try {
            // Fetch both endpoints
            val gpvHtml = api.fetchGpvHtml()
            val emergencyJson = api.fetchEmergencyJson()

            // Parse
            val schedule = parser.parseGpvHtml(gpvHtml)
            val emergencies = parser.parseEmergencyJson(emergencyJson)

            // Save last check time
            prefs.edit()
                .putString(KEY_LAST_CHECK_TIME, currentTimeFormatted())
                .apply()

            CheckResult(
                schedule = schedule,
                emergencies = emergencies
            )
        } catch (e: Exception) {
            CheckResult(
                schedule = null,
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
}
