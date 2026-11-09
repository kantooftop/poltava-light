package com.poltavalight.data

/**
 * Status of a single 30-minute time slot for a queue.
 */
enum class SlotStatus {
    ON,        // Power is on
    OFF,       // Power is off
    SWITCHING, // Switching period, power may be off
    UNKNOWN
}

/**
 * A single 30-minute time slot in the daily schedule.
 */
data class TimeSlot(
    val startHour: Int,
    val startMinute: Int, // 0 or 30
    val status: SlotStatus,
    val title: String = "" // e.g. "6.1: 08:00–08:30 — «Вимкнено»..."
)

/**
 * Full daily schedule for queue 6.1.
 */
data class DailySchedule(
    val date: String, // e.g. "9 листопада 2026"
    val slots: List<TimeSlot>,
    val summaryText: String = "", // "ГПВ в обсязі 4 черги" etc.
    val rawHash: String = "" // MD5 hash to detect changes
)

/**
 * Emergency outage event (GAV / SGAV / etc.).
 */
data class EmergencyEvent(
    val date: String,
    val beginHour: Int,
    val beginMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val typeName: String, // "ГАВ", "СГАВ", "ГОП"
    val typeId: Int,
    val reasonName: String,
    val note: String,
    val createdDate: String,
    val volume: Int
)

/**
 * Combined result of checking the POE website.
 */
data class CheckResult(
    val schedule: DailySchedule?,
    val emergencies: List<EmergencyEvent>,
    val error: String? = null
)
