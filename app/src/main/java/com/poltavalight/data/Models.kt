package com.poltavalight.data

import java.util.Locale

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
 * Full daily schedule for a queue.
 */
data class DailySchedule(
    val date: String, // e.g. "9 листопада 2026"
    val slots: List<TimeSlot>,
    val summaryText: String = "", // "ГПВ в обсязі 4 черги" etc.
    val rawHash: String = "" // MD5 hash to detect changes
) {
    /**
     * Total hours with light (ON), without light (OFF), and switching.
     */
    fun calculateStats(): ScheduleStats {
        var onSlots = 0
        var offSlots = 0
        var switchingSlots = 0

        for (slot in slots) {
            when (slot.status) {
                SlotStatus.ON -> onSlots++
                SlotStatus.OFF -> offSlots++
                SlotStatus.SWITCHING -> switchingSlots++
                SlotStatus.UNKNOWN -> {}
            }
        }

        return ScheduleStats(
            hoursOn = onSlots * 0.5,
            hoursOff = offSlots * 0.5,
            hoursSwitching = switchingSlots * 0.5
        )
    }

    /**
     * Smart Planner: finds the longest contiguous window with light (ON).
     */
    fun findLongestLightWindow(): LightWindow? {
        if (slots.isEmpty()) return null

        var bestStart = -1
        var bestCount = 0

        var currentStart = -1
        var currentCount = 0

        for (i in slots.indices) {
            if (slots[i].status == SlotStatus.ON) {
                if (currentCount == 0) {
                    currentStart = i
                }
                currentCount++
                if (currentCount > bestCount) {
                    bestCount = currentCount
                    bestStart = currentStart
                }
            } else {
                currentCount = 0
                currentStart = -1
            }
        }

        if (bestCount == 0 || bestStart == -1) return null

        val startSlot = slots[bestStart]
        val endSlotIndex = bestStart + bestCount - 1
        val lastSlot = slots[endSlotIndex]

        // End time is lastSlot start + 30 mins
        var endH = lastSlot.startHour
        var endM = lastSlot.startMinute + 30
        if (endM >= 60) {
            endH += 1
            endM = 0
        }

        return LightWindow(
            startHour = startSlot.startHour,
            startMinute = startSlot.startMinute,
            endHour = endH,
            endMinute = endM,
            durationMinutes = bestCount * 30
        )
    }

    /**
     * Creates a formatted summary text suitable for sharing in Telegram / messengers.
     */
    fun toShareText(queueName: String): String {
        val stats = calculateStats()
        val sb = StringBuilder()
        sb.append("⚡ Полтава Світло — $queueName\n")
        if (date.isNotBlank()) sb.append("📅 $date\n")
        sb.append("───────────────────\n")

        // Group slots into continuous blocks
        val blocks = groupSlotsIntoBlocks(slots)
        for (b in blocks) {
            val emoji = when (b.status) {
                SlotStatus.ON -> "✅"
                SlotStatus.OFF -> "⛔"
                SlotStatus.SWITCHING -> "⚠️"
                SlotStatus.UNKNOWN -> "❓"
            }
            val statusLabel = when (b.status) {
                SlotStatus.ON -> "Світло є"
                SlotStatus.OFF -> "Світла немає"
                SlotStatus.SWITCHING -> "Перемикання"
                SlotStatus.UNKNOWN -> "Невідомо"
            }
            sb.append(
                String.format(
                    Locale.US,
                    "%s %02d:%02d – %02d:%02d: %s (%s)\n",
                    emoji,
                    b.startHour, b.startMinute,
                    b.endHour, b.endMinute,
                    statusLabel,
                    formatDuration(b.durationMinutes)
                )
            )
        }

        sb.append("───────────────────\n")
        sb.append(String.format(Locale.US, "🟢 Зі світлом: %.1f год\n", stats.hoursOn))
        sb.append(String.format(Locale.US, "🔴 Без світла: %.1f год\n", stats.hoursOff))

        val window = findLongestLightWindow()
        if (window != null) {
            sb.append(
                String.format(
                    Locale.US,
                    "💡 Найдовше вікно зі світлом: %02d:%02d – %02d:%02d (%s)\n",
                    window.startHour, window.startMinute,
                    window.endHour, window.endMinute,
                    formatDuration(window.durationMinutes)
                )
            )
        }

        return sb.toString().trim()
    }
}

/**
 * Statistics for daily schedule.
 */
data class ScheduleStats(
    val hoursOn: Double,
    val hoursOff: Double,
    val hoursSwitching: Double
)

/**
 * Contiguous light window for Smart Planner.
 */
data class LightWindow(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val durationMinutes: Int
)

/**
 * Saved queue entry (e.g. Home, Parents).
 */
data class SavedQueue(
    val id: String,
    val label: String,      // e.g. "Дім"
    val queueNumber: String // e.g. "6.1"
)

/**
 * Continuous time block for display and grouping.
 */
data class ScheduleBlock(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val status: SlotStatus,
    val durationMinutes: Int
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
    val error: String? = null,
    val tomorrowSchedule: DailySchedule? = null // For previewing tomorrow
)

// Helper functions for slots grouping
fun groupSlotsIntoBlocks(slots: List<TimeSlot>): List<ScheduleBlock> {
    if (slots.isEmpty()) return emptyList()

    val sorted = slots.sortedWith(compareBy({ it.startHour }, { it.startMinute }))
    val blocks = mutableListOf<ScheduleBlock>()

    var currentStatus = sorted[0].status
    var blockStartH = sorted[0].startHour
    var blockStartM = sorted[0].startMinute
    var count = 1

    for (i in 1 until sorted.size) {
        val slot = sorted[i]
        if (slot.status == currentStatus) {
            count++
        } else {
            val endSlot = sorted[i - 1]
            var endH = endSlot.startHour
            var endM = endSlot.startMinute + 30
            if (endM >= 60) {
                endH += 1
                endM = 0
            }
            blocks.add(
                ScheduleBlock(
                    startHour = blockStartH,
                    startMinute = blockStartM,
                    endHour = endH,
                    endMinute = endM,
                    status = currentStatus,
                    durationMinutes = count * 30
                )
            )

            currentStatus = slot.status
            blockStartH = slot.startHour
            blockStartM = slot.startMinute
            count = 1
        }
    }

    val last = sorted.last()
    var endH = last.startHour
    var endM = last.startMinute + 30
    if (endM >= 60) {
        endH += 1
        endM = 0
    }
    blocks.add(
        ScheduleBlock(
            startHour = blockStartH,
            startMinute = blockStartM,
            endHour = endH,
            endMinute = endM,
            status = currentStatus,
            durationMinutes = count * 30
        )
    )

    return blocks
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h} год ${m} хв"
        h > 0 -> "$h год"
        else -> "$m хв"
    }
}
