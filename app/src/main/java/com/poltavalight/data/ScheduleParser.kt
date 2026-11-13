package com.poltavalight.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest

/**
 * Parses the HTML schedule table and JSON emergency data
 * from the POE website into structured data models.
 */
class ScheduleParser {

    companion object {
        const val TARGET_QUEUE = "4.2"
    }

    /**
     * Parse all 12 queues from the GPV HTML page in a single pass.
     * Returns a map of queueNumber (e.g. "4.2", "6.1") to DailySchedule.
     */
    fun parseAllQueuesFromHtml(html: String): Map<String, DailySchedule> {
        val resultMap = mutableMapOf<String, DailySchedule>()
        if (html.isBlank()) return resultMap

        try {
            val doc = Jsoup.parse(html)

            val summaryText = doc.select("div.gpvinfodetail").firstOrNull()?.text() ?: ""
            val dateText = doc.select("div.gpvinfodetail b").firstOrNull()?.text() ?: ""

            // Main schedule is in the horizontal table (class 'gpv-table' or first table)
            val mainTable = doc.select("table.gpv-table").firstOrNull() ?: doc.select("table").firstOrNull()
                ?: return resultMap

            val rows = mainTable.select("tr")
            for (row in rows) {
                val firstCell = row.select("td.gpv-q, th.gpv-q").firstOrNull() ?: continue
                val rawQueueText = firstCell.text().trim()

                // Extract queue number like "4.2", "6.1"
                val match = Regex("""(\d\.\d)""").find(rawQueueText) ?: continue
                val queueNum = match.groupValues[1]

                val cells = row.select("td.gpv-c")
                if (cells.isEmpty()) continue

                val slots = mutableListOf<TimeSlot>()
                for (cell in cells) {
                    val title = cell.attr("title")
                    val classes = cell.classNames()

                    val status = when {
                        "gpv-s1" in classes -> SlotStatus.ON
                        "gpv-s2" in classes -> SlotStatus.OFF
                        "gpv-s3" in classes -> SlotStatus.SWITCHING
                        else -> SlotStatus.UNKNOWN
                    }

                    val timeMatch = Regex("""(\d{1,2}):(\d{2})""").find(title)
                    val startHour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val startMinute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

                    slots.add(
                        TimeSlot(
                            startHour = startHour,
                            startMinute = startMinute,
                            status = status,
                            title = title
                        )
                    )
                }

                val sortedSlots = slots.sortedWith(compareBy({ it.startHour }, { it.startMinute }))
                val rawHash = md5(sortedSlots.joinToString { "${it.startHour}:${it.startMinute}=${it.status}" })

                resultMap[queueNum] = DailySchedule(
                    date = dateText,
                    slots = sortedSlots,
                    summaryText = summaryText,
                    rawHash = rawHash
                )
            }
        } catch (e: Exception) {
            // Ignore parse errors and return whatever was parsed
        }

        return resultMap
    }

    /**
     * Parse the GPV HTML page to extract schedule for a target queue (defaults to 4.2).
     */
    fun parseGpvHtml(html: String, targetQueue: String = TARGET_QUEUE): DailySchedule {
        val all = parseAllQueuesFromHtml(html)
        if (all.containsKey(targetQueue)) {
            return all[targetQueue]!!
        }

        // Fallback for custom / partial matches
        val doc = Jsoup.parse(html)
        val summaryText = doc.select("div.gpvinfodetail").firstOrNull()?.text() ?: ""
        val dateText = doc.select("div.gpvinfodetail b").firstOrNull()?.text() ?: ""

        val mainTable = doc.select("table.gpv-table").firstOrNull() ?: doc.select("table").firstOrNull()
        val slots = mutableListOf<TimeSlot>()

        if (mainTable != null) {
            for (row in mainTable.select("tr")) {
                val firstCell = row.select("td.gpv-q, th.gpv-q").firstOrNull() ?: continue
                val queueText = firstCell.text().trim()
                if (queueText.contains(targetQueue)) {
                    for (cell in row.select("td.gpv-c")) {
                        val title = cell.attr("title")
                        val classes = cell.classNames()

                        val status = when {
                            "gpv-s1" in classes -> SlotStatus.ON
                            "gpv-s2" in classes -> SlotStatus.OFF
                            "gpv-s3" in classes -> SlotStatus.SWITCHING
                            else -> SlotStatus.UNKNOWN
                        }

                        val timeMatch = Regex("""(\d{1,2}):(\d{2})""").find(title)
                        val startHour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val startMinute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

                        slots.add(
                            TimeSlot(
                                startHour = startHour,
                                startMinute = startMinute,
                                status = status,
                                title = title
                            )
                        )
                    }
                    break
                }
            }
        }

        val sortedSlots = slots.sortedWith(compareBy({ it.startHour }, { it.startMinute }))
        val rawHash = md5(sortedSlots.joinToString { "${it.startHour}:${it.startMinute}=${it.status}" })

        return DailySchedule(
            date = dateText,
            slots = sortedSlots,
            summaryText = summaryText,
            rawHash = rawHash
        )
    }

    /**
     * Parses tomorrow's schedule if available in a distinct second table or block.
     * Note: Ignores 'gpv-vtable' which is merely the mobile vertical view of today's schedule.
     */
    fun parseTomorrowGpvHtml(html: String, targetQueue: String = TARGET_QUEUE): DailySchedule? {
        val doc = Jsoup.parse(html)
        // Find only horizontal gpv-tables, not vertical mobile gpv-vtable
        val gpvTables = doc.select("table.gpv-table")
        if (gpvTables.size < 2) return null

        val tomorrowTable = gpvTables[1]
        val rows = tomorrowTable.select("tr")
        var tomorrowSlots: List<TimeSlot>? = null

        for (row in rows) {
            val firstCell = row.select("td.gpv-q, th.gpv-q").firstOrNull() ?: continue
            val queueText = firstCell.text().trim()
            if (queueText.contains(targetQueue)) {
                val slots = mutableListOf<TimeSlot>()
                for (cell in row.select("td.gpv-c")) {
                    val title = cell.attr("title")
                    val classes = cell.classNames()

                    val status = when {
                        "gpv-s1" in classes -> SlotStatus.ON
                        "gpv-s2" in classes -> SlotStatus.OFF
                        "gpv-s3" in classes -> SlotStatus.SWITCHING
                        else -> SlotStatus.UNKNOWN
                    }

                    val timeMatch = Regex("""(\d{1,2}):(\d{2})""").find(title)
                    val startHour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val startMinute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

                    slots.add(
                        TimeSlot(
                            startHour = startHour,
                            startMinute = startMinute,
                            status = status,
                            title = title
                        )
                    )
                }
                tomorrowSlots = slots.sortedWith(compareBy({ it.startHour }, { it.startMinute }))
                break
            }
        }

        if (tomorrowSlots.isNullOrEmpty()) return null

        val tomorrowHash = md5(tomorrowSlots.joinToString { "${it.startHour}:${it.startMinute}=${it.status}" })
        return DailySchedule(
            date = "Завтра",
            slots = tomorrowSlots,
            summaryText = "Опубліковано графік на завтра",
            rawHash = tomorrowHash
        )
    }

    /**
     * Parse emergency JSON response.
     */
    fun parseEmergencyJson(json: String): List<EmergencyEvent> {
        val events = mutableListOf<EmergencyEvent>()

        try {
            val trimmed = json.trim()
            val arr = if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                root.optJSONArray("value") ?: JSONArray()
            } else {
                JSONArray(trimmed)
            }

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                events.add(
                    EmergencyEvent(
                        date = obj.optString("unloaddata", ""),
                        beginHour = obj.optInt("beginhour", 0),
                        beginMinute = obj.optInt("beginminute", 0),
                        endHour = obj.optInt("endhour", 0),
                        endMinute = obj.optInt("endminute", 0),
                        typeName = obj.optString("unloadingtypename", ""),
                        typeId = obj.optInt("unloadingtypeid", 0),
                        reasonName = obj.optString("unloadingreasonname", ""),
                        note = obj.optString("note", ""),
                        createdDate = obj.optString("createddate", ""),
                        volume = obj.optInt("volume", 0)
                    )
                )
            }
        } catch (e: Exception) {
            // If JSON parsing fails, return empty list
        }

        return events
    }

    /**
     * Check if there are active emergency outages (GAV or SGAV).
     */
    fun hasActiveEmergency(events: List<EmergencyEvent>): Boolean {
        return events.any { it.typeId == 1 || it.typeId == 2 }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
