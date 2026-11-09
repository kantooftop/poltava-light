package com.poltavalight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poltavalight.data.*
import com.poltavalight.ui.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    checkResult: CheckResult?,
    lastCheckTime: String,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("⚡ Полтава Світло", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White
                ),
                actions = {
                    TextButton(onClick = onRefresh, enabled = !isLoading) {
                        Text(
                            if (isLoading) "⏳" else "🔄",
                            fontSize = 20.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Error state
            if (checkResult?.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "⚠️ ${checkResult.error}",
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            // Emergency banner
            if (checkResult != null && checkResult.emergencies.isNotEmpty()) {
                val gavEvents = checkResult.emergencies.filter {
                    it.typeId == 1 || it.typeId == 2
                }
                if (gavEvents.isNotEmpty()) {
                    item { EmergencyBanner(gavEvents) }
                }
            }

            // Current status
            if (checkResult?.schedule != null) {
                item { CurrentStatusCard(checkResult.schedule) }
            }

            // Summary
            if (checkResult?.schedule != null) {
                item { ScheduleSummaryCard(checkResult.schedule) }
            }

            // Timeline
            if (checkResult?.schedule != null && checkResult.schedule.slots.isNotEmpty()) {
                item {
                    Text(
                        "Графік на сьогодні — Черга 6.1",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Group slots into continuous blocks
                val blocks = groupSlotsIntoBlocks(checkResult.schedule.slots)
                items(blocks) { block ->
                    ScheduleBlockRow(block)
                }
            }

            // Last update info
            item {
                Text(
                    text = "Останнє оновлення: $lastCheckTime",
                    fontSize = 12.sp,
                    color = SubText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun EmergencyBanner(events: List<EmergencyEvent>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = EmergencyBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            for (event in events) {
                val typeLabel = when (event.typeId) {
                    1 -> "🚨 ГАВ — Графік аварійних відключень"
                    2 -> "🚨 СГАВ — Спеціальний аварійний графік"
                    else -> "⚠️ ${event.typeName}"
                }
                Text(
                    text = typeLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PowerOff
                )
                val time = String.format("%d:%02d", event.beginHour, event.beginMinute)
                Text(
                    text = "З $time • ${event.reasonName}",
                    fontSize = 14.sp,
                    color = DarkText
                )
                if (event.note.isNotBlank()) {
                    Text(
                        text = event.note,
                        fontSize = 13.sp,
                        color = SubText
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun CurrentStatusCard(schedule: DailySchedule) {
    val cal = Calendar.getInstance()
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = cal.get(Calendar.MINUTE)

    // Find current slot
    val currentSlot = schedule.slots.find { slot ->
        val slotEnd = if (slot.startMinute == 0) {
            slot.startHour * 60 + 30
        } else {
            slot.startHour * 60 + 60
        }
        val slotStart = slot.startHour * 60 + slot.startMinute
        val now = currentHour * 60 + currentMinute
        now in slotStart until slotEnd
    }

    val (statusEmoji, statusText, statusColor) = when (currentSlot?.status) {
        SlotStatus.ON -> Triple("✅", "Світло є", PowerOn)
        SlotStatus.OFF -> Triple("⛔", "Світла немає", PowerOff)
        SlotStatus.SWITCHING -> Triple("⚠️", "Перемикання", PowerSwitching)
        else -> Triple("❓", "Невідомо", SubText)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(statusEmoji, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Черга 6.1 зараз",
                    fontSize = 13.sp,
                    color = SubText
                )
                Text(
                    statusText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                if (currentSlot != null) {
                    val timeRange = String.format(
                        "%d:%02d – %d:%02d",
                        currentSlot.startHour, currentSlot.startMinute,
                        if (currentSlot.startMinute == 30) currentSlot.startHour + 1 else currentSlot.startHour,
                        if (currentSlot.startMinute == 30) 0 else 30
                    )
                    Text(
                        "Інтервал: $timeRange",
                        fontSize = 13.sp,
                        color = SubText
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleSummaryCard(schedule: DailySchedule) {
    if (schedule.date.isBlank()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = LightBlue),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                schedule.date,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PrimaryBlue
            )
        }
    }
}

/**
 * Group consecutive slots with the same status into blocks.
 */
data class ScheduleBlock(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val status: SlotStatus
)

fun groupSlotsIntoBlocks(slots: List<TimeSlot>): List<ScheduleBlock> {
    if (slots.isEmpty()) return emptyList()

    val blocks = mutableListOf<ScheduleBlock>()
    var currentStatus = slots.first().status
    var blockStartHour = slots.first().startHour
    var blockStartMinute = slots.first().startMinute

    for (i in 1 until slots.size) {
        val slot = slots[i]
        if (slot.status != currentStatus) {
            // End the current block
            blocks.add(
                ScheduleBlock(
                    startHour = blockStartHour,
                    startMinute = blockStartMinute,
                    endHour = slot.startHour,
                    endMinute = slot.startMinute,
                    status = currentStatus
                )
            )
            currentStatus = slot.status
            blockStartHour = slot.startHour
            blockStartMinute = slot.startMinute
        }
    }

    // Add last block
    val lastSlot = slots.last()
    val endMinute = if (lastSlot.startMinute == 30) 0 else 30
    val endHour = if (lastSlot.startMinute == 30) lastSlot.startHour + 1 else lastSlot.startHour
    blocks.add(
        ScheduleBlock(
            startHour = blockStartHour,
            startMinute = blockStartMinute,
            endHour = endHour,
            endMinute = endMinute,
            status = currentStatus
        )
    )

    return blocks
}

@Composable
fun ScheduleBlockRow(block: ScheduleBlock) {
    val (color, label) = when (block.status) {
        SlotStatus.ON -> Pair(PowerOn, "Світло є")
        SlotStatus.OFF -> Pair(PowerOff, "Світла немає")
        SlotStatus.SWITCHING -> Pair(PowerSwitching, "Перемикання")
        SlotStatus.UNKNOWN -> Pair(SubText, "Невідомо")
    }

    val timeRange = String.format(
        "%d:%02d – %d:%02d",
        block.startHour, block.startMinute,
        block.endHour, block.endMinute
    )

    // Check if this block contains the current time
    val cal = Calendar.getInstance()
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val blockStart = block.startHour * 60 + block.startMinute
    val blockEnd = block.endHour * 60 + block.endMinute
    val isCurrent = nowMinutes in blockStart until blockEnd

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) Modifier
                    .background(CurrentSlotBg, RoundedCornerShape(8.dp))
                    .border(2.dp, PrimaryBlue, RoundedCornerShape(8.dp))
                else Modifier
                    .background(CardBg, RoundedCornerShape(8.dp))
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(Modifier.width(12.dp))

        // Time range
        Text(
            text = timeRange,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier.width(100.dp)
        )

        // Status label
        Text(
            text = label,
            fontSize = 14.sp,
            color = color,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
        )

        if (isCurrent) {
            Spacer(Modifier.weight(1f))
            Text("← зараз", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
        }
    }
}
