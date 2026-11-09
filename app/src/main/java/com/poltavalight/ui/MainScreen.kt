package com.poltavalight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poltavalight.data.CheckResult
import com.poltavalight.data.DailySchedule
import com.poltavalight.data.EmergencyEvent
import com.poltavalight.data.SlotStatus
import com.poltavalight.data.TimeSlot
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
    val dark = isSystemInDarkTheme()

    val gavEvents = remember(checkResult) {
        val list: List<EmergencyEvent> = checkResult?.emergencies ?: emptyList<EmergencyEvent>()
        val filtered = mutableListOf<EmergencyEvent>()
        for (event in list) {
            if (event.typeId == 1 || event.typeId == 2) {
                filtered.add(event)
            }
        }
        filtered
    }

    val blocks = remember(checkResult) {
        val slots = checkResult?.schedule?.slots
        if (slots != null && slots.isNotEmpty()) {
            groupSlotsIntoBlocks(slots)
        } else {
            emptyList<ScheduleBlock>()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Полтава Світло",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Черга 6.1",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    FilledTonalIconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            if (isLoading) "⏳" else "🔄",
                            fontSize = 18.sp
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (checkResult?.error != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = checkResult.error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            if (gavEvents.isNotEmpty()) {
                item { PixelEmergencyBanner(gavEvents) }
            }

            if (checkResult?.schedule != null) {
                item {
                    PixelHeroCard(
                        schedule = checkResult.schedule,
                        dark = dark
                    )
                }
            }

            if (checkResult?.schedule != null && checkResult.schedule.date.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Графік відключень",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = checkResult.schedule.date,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (blocks.isNotEmpty()) {
                items(blocks) { block ->
                    PixelTimelineBlock(block = block, dark = dark)
                }
            }

            item {
                Text(
                    text = "Останнє оновлення: $lastCheckTime\nАТ «Полтаваобленерго»",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun PixelHeroCard(schedule: DailySchedule, dark: Boolean) {
    val cal = Calendar.getInstance()
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = cal.get(Calendar.MINUTE)
    val nowMinutes = currentHour * 60 + currentMinute

    val currentSlot = schedule.slots.find { slot ->
        val slotStart = slot.startHour * 60 + slot.startMinute
        val slotEnd = slotStart + 30
        nowMinutes in slotStart until slotEnd
    }

    val nextDifferentSlot = schedule.slots.find { slot ->
        val slotStart = slot.startHour * 60 + slot.startMinute
        slotStart > nowMinutes && slot.status != (currentSlot?.status ?: SlotStatus.UNKNOWN)
    }

    val (statusTitle, statusColor, containerColor, icon) = when (currentSlot?.status) {
        SlotStatus.ON -> Quadruple(
            "Світло є",
            if (dark) DarkPowerOn else PowerOn,
            if (dark) DarkPowerOnContainer.copy(alpha = 0.35f) else PowerOnContainer.copy(alpha = 0.5f),
            "💡"
        )
        SlotStatus.OFF -> Quadruple(
            "Світла немає",
            if (dark) DarkPowerOff else PowerOff,
            if (dark) DarkPowerOffContainer.copy(alpha = 0.35f) else PowerOffContainer.copy(alpha = 0.5f),
            "🌙"
        )
        SlotStatus.SWITCHING -> Quadruple(
            "Перемикання",
            if (dark) DarkPowerSwitching else PowerSwitching,
            if (dark) DarkPowerSwitchingContainer.copy(alpha = 0.35f) else PowerSwitchingContainer.copy(alpha = 0.5f),
            "⚡"
        )
        else -> Quadruple(
            "Статус невідомий",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
            "❓"
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ЗАРАЗ У ЧЕРЗІ 6.1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = statusColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = statusTitle,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = icon, fontSize = 28.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentSlot != null) {
                        val endMin = if (currentSlot.startMinute == 30) 0 else 30
                        val endH = if (currentSlot.startMinute == 30) currentSlot.startHour + 1 else currentSlot.startHour
                        Text(
                            text = String.format("Поточний слот: %02d:%02d – %02d:%02d", currentSlot.startHour, currentSlot.startMinute, endH, endMin),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (nextDifferentSlot != null) {
                        val diffMins = (nextDifferentSlot.startHour * 60 + nextDifferentSlot.startMinute) - nowMinutes
                        val hours = diffMins / 60
                        val mins = diffMins % 60
                        val timeWaitStr = if (hours > 0) "${hours}г ${mins}хв" else "${mins}хв"

                        val actionText = when (nextDifferentSlot.status) {
                            SlotStatus.OFF -> "Вимкнення через"
                            SlotStatus.ON -> "Увімкнення через"
                            else -> "Зміна через"
                        }
                        Text(
                            text = "$actionText $timeWaitStr",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PixelEmergencyBanner(events: List<EmergencyEvent>) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            for (event in events) {
                val label = when (event.typeId) {
                    1 -> "🚨 ГАВ — Графік аварійних відключень"
                    2 -> "🚨 СГАВ — Спеціальний аварійний графік"
                    else -> "⚠️ ${event.typeName}"
                }
                Text(
                    text = label,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                val time = String.format("%02d:%02d", event.beginHour, event.beginMinute)
                Text(
                    text = "Застосовано з $time • ${event.reasonName}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                )
                if (event.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = event.note,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun PixelTimelineBlock(block: ScheduleBlock, dark: Boolean) {
    val cal = Calendar.getInstance()
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val blockStart = block.startHour * 60 + block.startMinute
    val blockEnd = block.endHour * 60 + block.endMinute

    val isCurrent = nowMinutes in blockStart until blockEnd
    val isPast = nowMinutes >= blockEnd

    val (color, label, bgContainer) = when (block.status) {
        SlotStatus.ON -> Triple(
            if (dark) DarkPowerOn else PowerOn,
            "Світло є",
            if (dark) DarkPowerOnContainer.copy(alpha = 0.2f) else PowerOnContainer.copy(alpha = 0.35f)
        )
        SlotStatus.OFF -> Triple(
            if (dark) DarkPowerOff else PowerOff,
            "Світла немає",
            if (dark) DarkPowerOffContainer.copy(alpha = 0.2f) else PowerOffContainer.copy(alpha = 0.35f)
        )
        SlotStatus.SWITCHING -> Triple(
            if (dark) DarkPowerSwitching else PowerSwitching,
            "Перемикання",
            if (dark) DarkPowerSwitchingContainer.copy(alpha = 0.2f) else PowerSwitchingContainer.copy(alpha = 0.35f)
        )
        SlotStatus.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Невідомо",
            MaterialTheme.colorScheme.surfaceVariant
        )
    }

    val timeRange = String.format(
        "%02d:%02d – %02d:%02d",
        block.startHour, block.startMinute,
        block.endHour, block.endMinute
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isCurrent) bgContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) Modifier.border(2.dp, color, RoundedCornerShape(20.dp))
                else Modifier
            )
            .then(if (isPast) Modifier.alpha(0.55f) else Modifier.alpha(1f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(Modifier.width(14.dp))

            Text(
                text = timeRange,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(120.dp)
            )

            Text(
                text = label,
                fontSize = 14.sp,
                color = color,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold
            )

            if (isCurrent) {
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = color
                ) {
                    Text(
                        text = "Зараз",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

data class ScheduleBlock(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val status: SlotStatus
)

fun groupSlotsIntoBlocks(slots: List<TimeSlot>): List<ScheduleBlock> {
    if (slots.isEmpty()) return emptyList<ScheduleBlock>()

    val blocks = mutableListOf<ScheduleBlock>()
    var currentStatus = slots.first().status
    var blockStartHour = slots.first().startHour
    var blockStartMinute = slots.first().startMinute

    for (i in 1 until slots.size) {
        val slot = slots[i]
        if (slot.status != currentStatus) {
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
