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
        const val TARGET_QUEUE = "6.1"
    }

    /**
     * Parse the GPV HTML page to extract schedule for a target queue (defaults to 6.1).
     */
    fun parseGpvHtml(html: String, targetQueue: String = TARGET_QUEUE): DailySchedule {
        val doc = Jsoup.parse(html)

        // Extract date and summary from the top text block
        val summaryText = buildString {
            doc.select("div.gpvinfodetail b, div.gpvinfodetail").firstOrNull()?.let {
                append(doc.select("div.gpvinfodetail").first()?.ownText() ?: "")
            }
        }

        val dateText = doc.select("div.gpvinfodetail b").firstOrNull()?.text() ?: ""

        val tables = doc.select("table")
        val slots = mutableListOf<TimeSlot>()

        for (table in tables) {
            val rows = table.select("tr")
            for (row in rows) {
                val firstCell = row.select("td.gpv-q, th.gpv-q").firstOrNull()
                    ?: continue

                val queueText = firstCell.text().trim()
                if (queueText != targetQueue) continue

                // This is our row — parse all time slot cells
                val cells = row.select("td.gpv-c")
                for (cell in cells) {
                    val title = cell.attr("title")
                    val classes = cell.classNames()

                    val status = when {
                        "gpv-s1" in classes -> SlotStatus.ON
                        "gpv-s2" in classes -> SlotStatus.OFF
                        "gpv-s3" in classes -> SlotStatus.SWITCHING
                        else -> SlotStatus.UNKNOWN
                    }

                    // Parse time from title like "6.1: 08:00–08:30 — ..."
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
                break // Found our queue row in this table, stop searching rows
            }
            if (slots.isNotEmpty()) break // Found today's data, stop searching tables
        }

        // Compute hash of the schedule to detect changes
        val rawHash = md5(slots.joinToString { "${it.startHour}:${it.startMinute}=${it.status}" })

        return DailySchedule(
            date = dateText,
            slots = slots.sortedWith(compareBy({ it.startHour }, { it.startMinute })),
            summaryText = summaryText,
            rawHash = rawHash
        )
    }

    /**
     * Parses tomorrow's schedule if available in a second table or block on the page.
     */
    fun parseTomorrowGpvHtml(html: String, targetQueue: String = TARGET_QUEUE): DailySchedule? {
        val doc = Jsoup.parse(html)
        val tables = doc.select("table")
        if (tables.size < 2) return null

        // Usually table 1 is today, table 2 is tomorrow if published
        var foundFirst = false
        var tomorrowSlots: List<TimeSlot>? = null

        for (table in tables) {
            val rows = table.select("tr")
            for (row in rows) {
                val firstCell = row.select("td.gpv-q, th.gpv-q").firstOrNull() ?: continue
                if (firstCell.text().trim() == targetQueue) {
                    if (!foundFirst) {
                        foundFirst = true
                        break
                    } else {
                        // Found second table's queue row!
                        val slots = mutableListOf<TimeSlot>()
                        val cells = row.select("td.gpv-c")
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
                        tomorrowSlots = slots.sortedWith(compareBy({ it.startHour }, { it.startMinute }))
                        break
                    }
                }
            }
            if (tomorrowSlots != null) break
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
