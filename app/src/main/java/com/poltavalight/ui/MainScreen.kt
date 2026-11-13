package com.poltavalight.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poltavalight.data.CheckResult
import com.poltavalight.data.DailySchedule
import com.poltavalight.data.EmergencyEvent
import com.poltavalight.data.LightWindow
import com.poltavalight.data.SavedQueue
import com.poltavalight.data.ScheduleBlock
import com.poltavalight.data.ScheduleStats
import com.poltavalight.data.SlotStatus
import com.poltavalight.data.TimeSlot
import com.poltavalight.data.formatDuration
import com.poltavalight.data.groupSlotsIntoBlocks
import com.poltavalight.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    checkResult: CheckResult?,
    lastCheckTime: String,
    isLoading: Boolean,
    isCharging: Boolean,
    selectedQueueNumber: String,
    selectedQueueLabel: String,
    savedQueues: List<SavedQueue>,
    isNotificationsEnabled: Boolean,
    onQueueSelected: (SavedQueue) -> Unit,
    onToggleNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onShare: (String) -> Unit
) {
    var selectedDayIndex by remember { mutableIntStateOf(0) } // 0 = Сьогодні, 1 = Завтра
    var showQueueDialog by remember { mutableStateOf(false) }
    var showPastSlots by remember { mutableStateOf(false) }

    // Ticking time for countdown
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val cal = remember(currentTimeMillis) {
        Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    }
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = cal.get(Calendar.MINUTE)
    val currentSecond = cal.get(Calendar.SECOND)
    val currentTotalMinutes = currentHour * 60 + currentMinute

    // Active schedule based on selected day
    val activeSchedule: DailySchedule? = remember(checkResult, selectedDayIndex) {
        if (selectedDayIndex == 1 && checkResult?.tomorrowSchedule != null) {
            checkResult.tomorrowSchedule
        } else {
            checkResult?.schedule
        }
    }

    val hasTomorrowSchedule = checkResult?.tomorrowSchedule != null

    // Filter active GAV/SGAV
    val activeEmergencies: List<EmergencyEvent> = remember(checkResult) {
        val list: List<EmergencyEvent> = checkResult?.emergencies ?: emptyList()
        list.filter { event: EmergencyEvent -> event.typeId == 1 || event.typeId == 2 }
    }

    // Slots & Blocks
    val slots: List<TimeSlot> = activeSchedule?.slots ?: emptyList()
    val blocks: List<ScheduleBlock> = remember(slots) { groupSlotsIntoBlocks(slots) }

    // Current slot (only valid for "today")
    val currentSlot: TimeSlot? = remember(slots, currentTotalMinutes, selectedDayIndex) {
        if (selectedDayIndex == 0 && slots.isNotEmpty()) {
            slots.find { slot: TimeSlot ->
                val slotStart = slot.startHour * 60 + slot.startMinute
                currentTotalMinutes in slotStart until (slotStart + 30)
            } ?: slots.firstOrNull()
        } else {
            slots.firstOrNull()
        }
    }

    val currentStatus: SlotStatus = currentSlot?.status ?: SlotStatus.UNKNOWN

    // Next change slot for countdown
    val nextChangeSlot: TimeSlot? = remember(slots, currentTotalMinutes, selectedDayIndex, currentStatus) {
        if (selectedDayIndex == 0) {
            slots.find { slot: TimeSlot ->
                val slotStart = slot.startHour * 60 + slot.startMinute
                slotStart > currentTotalMinutes && slot.status != currentStatus
            }
        } else {
            slots.firstOrNull()
        }
    }

    // Subtitle sequence of future events
    val futureSubtitle = remember(blocks, currentTotalMinutes, selectedDayIndex) {
        if (selectedDayIndex == 0) {
            val future = blocks.filter { (it.endHour * 60 + it.endMinute) > currentTotalMinutes }
            if (future.isEmpty()) {
                "До кінця доби змін не очікується"
            } else {
                val next1 = future.getOrNull(0)
                val next2 = future.getOrNull(1)
                buildString {
                    append("Далі: ")
                    if (next1 != null) {
                        append(getShortStatusLabel(next1.status))
                        append(String.format(Locale.US, " о %02d:%02d", next1.startHour, next1.startMinute))
                    }
                    if (next2 != null) {
                        append(" • ")
                        append(getShortStatusLabel(next2.status))
                        append(String.format(Locale.US, " о %02d:%02d", next2.startHour, next2.startMinute))
                    }
                }
            }
        } else {
            "Графік на наступний день"
        }
    }

    // Statistics & Planner
    val stats = remember(activeSchedule) { activeSchedule?.calculateStats() }
    val longestWindow = remember(activeSchedule) { activeSchedule?.findLongestLightWindow() }

    Scaffold(
        containerColor = RevolutBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Top Header Row
                HeaderBar(
                    queueLabel = selectedQueueLabel,
                    queueNumber = selectedQueueNumber,
                    selectedDayIndex = selectedDayIndex,
                    hasTomorrowSchedule = hasTomorrowSchedule,
                    onQueueClick = { showQueueDialog = true },
                    onDayChanged = { selectedDayIndex = it }
                )
            }

            // Emergency GAV/SGAV Banner
            if (activeEmergencies.isNotEmpty()) {
                item {
                    EmergencyBanner(events = activeEmergencies)
                }
            }

            // Hero Block: Revolut Style Balance / Timer
            item {
                HeroTimerCard(
                    status = currentStatus,
                    isCharging = isCharging,
                    selectedDayIndex = selectedDayIndex,
                    nextSlot = nextChangeSlot,
                    currentHour = currentHour,
                    currentMinute = currentMinute,
                    currentSecond = currentSecond,
                    subtitle = futureSubtitle
                )
            }

            // Action Buttons Row (Refresh, Notifications, Queues, Share)
            item {
                ActionButtonsRow(
                    isLoading = isLoading,
                    lastCheckTime = lastCheckTime,
                    isNotificationsEnabled = isNotificationsEnabled,
                    onRefresh = onRefresh,
                    onToggleNotifications = onToggleNotifications,
                    onOpenQueues = { showQueueDialog = true },
                    onShare = {
                        val text = activeSchedule?.toShareText(selectedQueueLabel)
                            ?: "Графік відключень $selectedQueueLabel\nДані відсутні"
                        onShare(text)
                    }
                )
            }

            // 24-Hour Visual Timeline Card & Day Statistics
            item {
                TimelineAndStatsCard(
                    schedule = activeSchedule,
                    currentTotalMinutes = if (selectedDayIndex == 0) currentTotalMinutes else -1,
                    stats = stats,
                    longestWindow = longestWindow
                )
            }

            // Intervals List Card (Revolut History Style)
            item {
                IntervalsCard(
                    blocks = blocks,
                    currentTotalMinutes = if (selectedDayIndex == 0) currentTotalMinutes else -1,
                    showPastSlots = showPastSlots,
                    onToggleShowPast = { showPastSlots = !showPastSlots }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Queue selection Dialog
    if (showQueueDialog) {
        QueueSelectionDialog(
            currentQueueNumber = selectedQueueNumber,
            savedQueues = savedQueues,
            onDismiss = { showQueueDialog = false },
            onSelect = { q ->
                onQueueSelected(q)
                showQueueDialog = false
            }
        )
    }
}

// ====================================================================
// HEADER BAR: Queue Capsule + Day Switcher
// ====================================================================
@Composable
private fun HeaderBar(
    queueLabel: String,
    queueNumber: String,
    selectedDayIndex: Int,
    hasTomorrowSchedule: Boolean,
    onQueueClick: () -> Unit,
    onDayChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Queue Selector Capsule (e.g. "Черга 6.1 ▾")
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = RevolutSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onQueueClick() }
                .border(1.dp, RevolutCardBorder, RoundedCornerShape(20.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$queueLabel ($queueNumber)",
                    color = RevolutTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Оберіть чергу",
                    tint = RevolutTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Segmented Day Switcher: Сьогодні / Завтра
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = RevolutSecondary,
            modifier = Modifier
                .border(1.dp, RevolutCardBorder, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DaySegment(
                    text = "Сьогодні",
                    isSelected = selectedDayIndex == 0,
                    onClick = { onDayChanged(0) }
                )
                DaySegment(
                    text = "Завтра",
                    isSelected = selectedDayIndex == 1,
                    hasBadge = hasTomorrowSchedule,
                    onClick = { onDayChanged(1) }
                )
            }
        }
    }
}

@Composable
private fun DaySegment(
    text: String,
    isSelected: Boolean,
    hasBadge: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) RevolutCard else Color.Transparent
    val textColor = if (isSelected) RevolutTextPrimary else RevolutTextSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (hasBadge) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PowerOn)
                )
            }
        }
    }
}

// ====================================================================
// HERO CARD: Large Revolut-Style Timer & Status
// ====================================================================
@Composable
private fun HeroTimerCard(
    status: SlotStatus,
    isCharging: Boolean,
    selectedDayIndex: Int,
    nextSlot: TimeSlot?,
    currentHour: Int,
    currentMinute: Int,
    currentSecond: Int,
    subtitle: String
) {
    // Pulse animation for status dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaDot by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    // Compute remaining time in hh:mm:ss
    val timerString = remember(nextSlot, currentHour, currentMinute, currentSecond, selectedDayIndex) {
        if (selectedDayIndex == 1 || nextSlot == null) {
            when (status) {
                SlotStatus.ON -> "Світло є"
                SlotStatus.OFF -> "Світла немає"
                SlotStatus.SWITCHING -> "Перемикання"
                SlotStatus.UNKNOWN -> "— : —"
            }
        } else {
            val targetMinutes = nextSlot.startHour * 60 + nextSlot.startMinute
            val currentMinutes = currentHour * 60 + currentMinute
            var totalDiffSeconds = (targetMinutes - currentMinutes) * 60 - currentSecond
            if (totalDiffSeconds < 0) totalDiffSeconds = 0

            val h = totalDiffSeconds / 3600
            val m = (totalDiffSeconds % 3600) / 60
            val s = totalDiffSeconds % 60
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        }
    }

    val (statusColor, statusLabel) = when (status) {
        SlotStatus.ON -> Pair(PowerOn, "Світло є")
        SlotStatus.OFF -> Pair(PowerOff, "Світла немає")
        SlotStatus.SWITCHING -> Pair(PowerSwitching, "Перемикання")
        SlotStatus.UNKNOWN -> Pair(PowerUnknown, "Невідомо")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RevolutCardBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = RevolutCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badges row: Status Badge + Live Power Source Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status Badge with pulsing dot
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                                .alpha(alphaDot)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Live Power Source Badge (Charging / Battery)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RevolutSecondary,
                    modifier = Modifier.border(1.dp, RevolutCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCharging) "⚡ Мережа є" else "🔋 Від батареї",
                            color = if (isCharging) PowerOn else RevolutTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large Hero Countdown Timer (Revolut balance style)
            Text(
                text = timerString,
                color = RevolutTextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle: Next event details
            Text(
                text = subtitle,
                color = RevolutTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ====================================================================
// ACTION BUTTONS ROW: 4 Round Buttons
// ====================================================================
@Composable
private fun ActionButtonsRow(
    isLoading: Boolean,
    lastCheckTime: String,
    isNotificationsEnabled: Boolean,
    onRefresh: () -> Unit,
    onToggleNotifications: () -> Unit,
    onOpenQueues: () -> Unit,
    onShare: () -> Unit
) {
    // Rotation animation for refresh button
    val infiniteTransition = rememberInfiniteTransition(label = "refreshAnim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 1. Оновити
        ActionButtonItem(
            icon = Icons.Default.Refresh,
            label = "Оновити",
            iconRotation = if (isLoading) rotation else 0f,
            onClick = onRefresh
        )

        // 2. Сповіщення
        ActionButtonItem(
            icon = if (isNotificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            label = "Сповіщення",
            activeColor = if (isNotificationsEnabled) PowerOn else RevolutTextSecondary,
            onClick = onToggleNotifications
        )

        // 3. Черги
        ActionButtonItem(
            icon = Icons.Default.ViewList,
            label = "Черги",
            onClick = onOpenQueues
        )

        // 4. Поділитися
        ActionButtonItem(
            icon = Icons.Default.Share,
            label = "Поділитися",
            onClick = onShare
        )
    }
}

@Composable
private fun ActionButtonItem(
    icon: ImageVector,
    label: String,
    iconRotation: Float = 0f,
    activeColor: Color = RevolutTextPrimary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = RevolutSecondary,
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, RevolutCardBorder, CircleShape)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = activeColor,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(iconRotation)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = RevolutTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ====================================================================
// 24-HOUR TIMELINE CARD & STATISTICS + SMART PLANNER
// ====================================================================
@Composable
private fun TimelineAndStatsCard(
    schedule: DailySchedule?,
    currentTotalMinutes: Int,
    stats: ScheduleStats?,
    longestWindow: LightWindow?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RevolutCardBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = RevolutCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Добова шкала (24 год)",
                color = RevolutTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Continuous 24h Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RevolutSecondary)
            ) {
                val slots = schedule?.slots ?: emptyList()
                if (slots.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (slot in slots) {
                            val color = when (slot.status) {
                                SlotStatus.ON -> PowerOn
                                SlotStatus.OFF -> PowerOff
                                SlotStatus.SWITCHING -> PowerSwitching
                                SlotStatus.UNKNOWN -> RevolutSecondary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(color)
                            )
                        }
                    }
                }

                // Vertical White Marker for Current Time
                if (currentTotalMinutes in 0..1439) {
                    val markerFraction = currentTotalMinutes.toFloat() / 1440f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = markerFraction)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                }
            }

            // Hour Markers (00, 06, 12, 18, 24)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00", "06", "12", "18", "24").forEach { hourStr ->
                    Text(
                        text = hourStr,
                        color = RevolutTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = RevolutCardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 2 Columns of Day Totals
            if (stats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f год зі світлом", stats.hoursOn),
                            color = PowerOn,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🌑", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f год без світла", stats.hoursOff),
                            color = PowerOff,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Smart Planner Window Hint
            if (longestWindow != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RevolutSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RevolutCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "💡", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "Найдовше вікно зі світлом: %02d:%02d – %02d:%02d (%s)",
                                longestWindow.startHour, longestWindow.startMinute,
                                longestWindow.endHour, longestWindow.endMinute,
                                formatDuration(longestWindow.durationMinutes)
                            ),
                            color = RevolutTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// GROUPED INTERVALS CARD: Revolut Transaction History Style
// ====================================================================
@Composable
private fun IntervalsCard(
    blocks: List<ScheduleBlock>,
    currentTotalMinutes: Int,
    showPastSlots: Boolean,
    onToggleShowPast: () -> Unit
) {
    if (blocks.isEmpty()) return

    // Partition into past and current/future
    val (pastBlocks, activeOrFutureBlocks) = if (currentTotalMinutes >= 0) {
        blocks.partition { (it.endHour * 60 + it.endMinute) <= currentTotalMinutes }
    } else {
        Pair(emptyList(), blocks)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RevolutCardBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = RevolutCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Розклад інтервалів",
                    color = RevolutTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                // Button to toggle past slots if any
                if (pastBlocks.isNotEmpty()) {
                    Text(
                        text = if (showPastSlots) "Сховати минулі" else "Минулі (${pastBlocks.size})",
                        color = PowerOn,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleShowPast() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Past intervals (collapsed by default, dimmed)
            AnimatedVisibility(visible = showPastSlots) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pastBlocks.forEach { block ->
                        IntervalRow(block = block, isCurrent = false, isPast = true)
                    }
                    HorizontalDivider(color = RevolutCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            // Current & Future intervals
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeOrFutureBlocks.forEach { block ->
                    val isCurrent = currentTotalMinutes >= 0 &&
                            currentTotalMinutes in (block.startHour * 60 + block.startMinute) until (block.endHour * 60 + block.endMinute)
                    IntervalRow(block = block, isCurrent = isCurrent, isPast = false)
                }
            }
        }
    }
}

@Composable
private fun IntervalRow(
    block: ScheduleBlock,
    isCurrent: Boolean,
    isPast: Boolean
) {
    val (color, label) = when (block.status) {
        SlotStatus.ON -> Pair(PowerOn, "Світло є")
        SlotStatus.OFF -> Pair(PowerOff, "Світла немає")
        SlotStatus.SWITCHING -> Pair(PowerSwitching, "Перемикання")
        SlotStatus.UNKNOWN -> Pair(PowerUnknown, "Невідомо")
    }

    val alpha = if (isPast) 0.45f else 1.0f
    val bg = if (isCurrent) color.copy(alpha = 0.12f) else Color.Transparent

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon Circle
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Time & Status
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "%02d:%02d – %02d:%02d",
                            block.startHour, block.startMinute,
                            block.endHour, block.endMinute
                        ),
                        color = RevolutTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = color.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "Зараз",
                                color = color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = label,
                    color = RevolutTextSecondary,
                    fontSize = 12.sp
                )
            }

            // Duration on the right
            Text(
                text = formatDuration(block.durationMinutes),
                color = RevolutTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ====================================================================
// EMERGENCY ALERT BANNER
// ====================================================================
@Composable
private fun EmergencyBanner(events: List<EmergencyEvent>) {
    val event = events.firstOrNull() ?: return
    val label = if (event.typeId == 2) "СГАВ" else "ГАВ"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PowerOff.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF2A0E14)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🚨", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$label — Аварійне відключення!",
                    color = PowerOff,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "З ${event.beginHour}:${String.format(Locale.US, "%02d", event.beginMinute)} ${event.reasonName}. ${event.note}".trim(),
                    color = RevolutTextPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ====================================================================
// QUEUE SELECTION DIALOG
// ====================================================================
@Composable
private fun QueueSelectionDialog(
    currentQueueNumber: String,
    savedQueues: List<SavedQueue>,
    onDismiss: () -> Unit,
    onSelect: (SavedQueue) -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RevolutCard,
        title = {
            Text(
                text = "Оберіть чергу (Полтавська обл.)",
                color = RevolutTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section: All 12 queues of Poltava region
                Text(
                    text = "Всі черги Полтавщини (ГПВ)",
                    color = RevolutTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // 6 queues x 2 subqueues grid
                val queuePairs = listOf(
                    "1.1" to "1.2",
                    "2.1" to "2.2",
                    "3.1" to "3.2",
                    "4.1" to "4.2",
                    "5.1" to "5.2",
                    "6.1" to "6.2"
                )

                queuePairs.forEach { (q1, q2) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QueueButton(
                            queueNumber = q1,
                            isSelected = currentQueueNumber == q1,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSelect(SavedQueue(id = q1, label = "Черга $q1", queueNumber = q1))
                            }
                        )
                        QueueButton(
                            queueNumber = q2,
                            isSelected = currentQueueNumber == q2,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSelect(SavedQueue(id = q2, label = "Черга $q2", queueNumber = q2))
                            }
                        )
                    }
                }

                // Section: Saved bookmarks (if any)
                if (savedQueues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Швидкі закладки",
                        color = RevolutTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    savedQueues.forEach { queue ->
                        val isSelected = queue.queueNumber == currentQueueNumber
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) RevolutSecondary else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(queue) }
                                .border(
                                    1.dp,
                                    if (isSelected) PowerOn else RevolutCardBorder,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${queue.label} (Черга ${queue.queueNumber})",
                                    color = RevolutTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        color = PowerOn,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Закрити", color = PowerOn)
            }
        }
    )
}

@Composable
private fun QueueButton(
    queueNumber: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PowerOn.copy(alpha = 0.2f) else RevolutSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (isSelected) PowerOn else RevolutCardBorder,
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Черга $queueNumber",
                color = if (isSelected) PowerOn else RevolutTextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "✓",
                    color = PowerOn,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun getShortStatusLabel(status: SlotStatus): String {
    return when (status) {
        SlotStatus.ON -> "Світло"
        SlotStatus.OFF -> "Вимкнення"
        SlotStatus.SWITCHING -> "Перемикання"
        SlotStatus.UNKNOWN -> "—"
    }
}
