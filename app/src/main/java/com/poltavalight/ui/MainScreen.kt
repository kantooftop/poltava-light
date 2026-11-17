package com.poltavalight.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // High precision ticking time for countdown
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
        if (selectedDayIndex == 1) {
            checkResult?.tomorrowSchedule
        } else {
            checkResult?.schedule
        }
    }

    val hasTomorrowSchedule = checkResult?.tomorrowSchedule != null

    // Filter active emergency events (GAV / SGAV)
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

    // Future subtitle sequence
    val futureSubtitle = remember(blocks, currentTotalMinutes, selectedDayIndex, slots.isEmpty(), currentStatus) {
        if (slots.isEmpty()) {
            "Дані графіка відсутні"
        } else if (selectedDayIndex == 0) {
            val nextBlock = blocks.firstOrNull { 
                (it.startHour * 60 + it.startMinute) > currentTotalMinutes && it.status != currentStatus 
            }
            if (nextBlock == null) {
                "До кінця доби змін не очікується"
            } else {
                val action = when (nextBlock.status) {
                    SlotStatus.OFF -> "Вимкнення"
                    SlotStatus.ON -> "Увімкнення"
                    SlotStatus.SWITCHING -> "Можливе перемикання"
                    else -> "Зміна графіка"
                }
                String.format(Locale.US, "%s о %02d:%02d", action, nextBlock.startHour, nextBlock.startMinute)
            }
        } else {
            "Графік на наступний день"
        }
    }

    val stats = remember(activeSchedule) { activeSchedule?.calculateStats() }
    val longestWindow = remember(activeSchedule) { activeSchedule?.findLongestLightWindow() }

    Scaffold(
        containerColor = RevolutBlack
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    // Header: Queue capsule and animated day segmented switch
                    HeaderBar(
                        queueNumber = selectedQueueNumber,
                        selectedDayIndex = selectedDayIndex,
                        hasTomorrowSchedule = hasTomorrowSchedule,
                        onQueueClick = { showQueueDialog = true },
                        onDayChanged = { selectedDayIndex = it }
                    )
                }

                // VPN / Regional Network Banner
                if (checkResult?.error != null) {
                    item {
                        VpnErrorBanner(
                            errorMessage = checkResult.error,
                            hasCachedData = activeSchedule != null,
                            lastCheckTime = lastCheckTime,
                            onRetry = onRefresh
                        )
                    }
                }

                // Emergency GAV / SGAV Banner
                if (activeEmergencies.isNotEmpty()) {
                    item {
                        EmergencyBanner(events = activeEmergencies)
                    }
                }

                if (selectedDayIndex == 1 && activeSchedule == null) {
                    item {
                        EmptyTomorrowCard(
                            isLoading = isLoading,
                            onRefresh = onRefresh
                        )
                    }
                } else {
                    // Hero Card: Specular gradient border, ambient aura glow, living beacon pulse
                    item {
                        HeroTimerCard(
                            status = currentStatus,
                            selectedDayIndex = selectedDayIndex,
                            nextSlot = nextChangeSlot,
                            currentHour = currentHour,
                            currentMinute = currentMinute,
                            currentSecond = currentSecond,
                            subtitle = futureSubtitle
                        )
                    }

                    // Tactile Action Buttons Row with gradient rims
                    item {
                        ActionButtonsRow(
                            isLoading = isLoading,
                            lastCheckTime = lastCheckTime,
                            isNotificationsEnabled = isNotificationsEnabled,
                            onRefresh = onRefresh,
                            onToggleNotifications = onToggleNotifications,
                            onOpenQueues = { showQueueDialog = true },
                            onShare = {
                                val text = activeSchedule?.toShareText("Черга $selectedQueueNumber")
                                    ?: "Графік відключень Черга $selectedQueueNumber\nДані відсутні"
                                onShare(text)
                            }
                        )
                    }

                    // 24-Hour Visual Timeline Card & Bento Stats
                    if (activeSchedule != null) {
                        item {
                            TimelineAndStatsCard(
                                schedule = activeSchedule,
                                currentTotalMinutes = if (selectedDayIndex == 0) currentTotalMinutes else -1,
                                stats = stats,
                                longestWindow = longestWindow,
                                onRefresh = onRefresh
                            )
                        }
                    }

                    // Intervals List Card (Collapsible, connecting timeline feel)
                    if (blocks.isNotEmpty()) {
                        item {
                            IntervalsCard(
                                blocks = blocks,
                                currentTotalMinutes = if (selectedDayIndex == 0) currentTotalMinutes else -1,
                                showPastSlots = showPastSlots,
                                onToggleShowPast = { showPastSlots = !showPastSlots }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // Top Progress Indicator with gradient accent
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .height(3.dp),
                    color = AccentBlue,
                    trackColor = Color.Transparent
                )
            }
        }
    }

    // Queue selection Dialog
    if (showQueueDialog) {
        QueueSelectionDialog(
            currentQueueNumber = selectedQueueNumber,
            onDismiss = { showQueueDialog = false },
            onSelect = { q ->
                onQueueSelected(SavedQueue(id = q, label = "Черга $q", queueNumber = q))
                showQueueDialog = false
            }
        )
    }
}

// ====================================================================
// HEADER BAR: Capsule Queue Selector + Animated Day Switcher
// ====================================================================
@Composable
private fun HeaderBar(
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
        // Queue Selector Capsule: Glass-tint with specular gradient border
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = RevolutSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable { onQueueClick() }
                .border(
                    width = 1.dp,
                    brush = CardBorderBrush,
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PowerOn)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Черга $queueNumber",
                    color = RevolutTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Оберіть чергу",
                    tint = RevolutTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Sliding Segmented Day Switcher (Сьогодні / Завтра)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = RevolutSecondary,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    brush = CardBorderBrush,
                    shape = RoundedCornerShape(22.dp)
                )
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
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) RevolutSurfaceHighlight else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "segmentBg"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) RevolutTextPrimary else RevolutTextSecondary,
        animationSpec = tween(durationMillis = 220),
        label = "segmentText"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = animatedBg,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = animatedTextColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (hasBadge) {
                Spacer(modifier = Modifier.width(6.dp))
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
// HERO CARD: Specular Gradient Border + Radial Ambient Glow + Radar Beacon
// ====================================================================
@Composable
private fun HeroTimerCard(
    status: SlotStatus,
    selectedDayIndex: Int,
    nextSlot: TimeSlot?,
    currentHour: Int,
    currentMinute: Int,
    currentSecond: Int,
    subtitle: String
) {
    val (statusColor, statusLabel) = when (status) {
        SlotStatus.ON -> Pair(PowerOn, "Світло є")
        SlotStatus.OFF -> Pair(PowerOff, "Світла немає")
        SlotStatus.SWITCHING -> Pair(PowerSwitching, "Можливе перемикання")
        SlotStatus.UNKNOWN -> Pair(PowerUnknown, "Невідомо")
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "heroStatusColor"
    )

    // Radar pulse animation for living beacon
    val infiniteTransition = rememberInfiniteTransition(label = "beaconPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Format timer
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

    // Top-down specular gradient border with active color accent (css-border-gradient)
    val heroBorderBrush = Brush.verticalGradient(
        colors = listOf(
            animatedStatusColor.copy(alpha = 0.45f),
            Color(0x18FFFFFF),
            Color(0x05FFFFFF)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(RevolutCard)
            .border(width = 1.dp, brush = heroBorderBrush, shape = RoundedCornerShape(26.dp))
    ) {
        // Ambient soft radial glow behind the timer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedStatusColor.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High-Tech Status Pill with Radar Beacon
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = animatedStatusColor.copy(alpha = 0.12f),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = animatedStatusColor.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(14.dp)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Concentric Living Beacon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(16.dp)
                    ) {
                        // Expanding wave
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(animatedStatusColor)
                        )
                        // Solid core
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(animatedStatusColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = statusLabel,
                        color = animatedStatusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Large Tabular Countdown (better-typography: tnum prevents jitter)
            Text(
                text = timerString,
                color = RevolutTextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 44.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle sequence with refined contrast
            Text(
                text = subtitle,
                color = RevolutTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ====================================================================
// ACTION BUTTONS ROW: Refresh, Notifications, Queues, Share
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
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Оновити
        ActionButtonItem(
            icon = Icons.Default.Refresh,
            label = "Оновити",
            iconRotation = if (isLoading) rotation else 0f,
            activeColor = if (isLoading) AccentBlue else RevolutTextPrimary,
            onClick = onRefresh
        )

        // 2. Сповіщення
        ActionButtonItem(
            icon = if (isNotificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            label = "Сповіщення",
            activeColor = if (isNotificationsEnabled) PowerOn else RevolutTextMuted,
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
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = RevolutSecondary,
            modifier = Modifier
                .size(52.dp)
                .border(
                    width = 1.dp,
                    brush = CardBorderBrush,
                    shape = CircleShape
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
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
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = label,
            color = RevolutTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ====================================================================
// 24-HOUR TIMELINE & BENTO STATS + SMART PLANNER
// ====================================================================
@Composable
private fun TimelineAndStatsCard(
    schedule: DailySchedule?,
    currentTotalMinutes: Int,
    stats: ScheduleStats?,
    longestWindow: LightWindow?,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, brush = CardBorderBrush, shape = RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = RevolutCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ДОБОВА ШКАЛА (24 ГОД)",
                    color = RevolutTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "48 сегментів",
                    color = RevolutTextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val slots = schedule?.slots ?: emptyList()

            if (slots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RevolutSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Натисніть «Оновити» для завантаження графіка",
                        color = RevolutTextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                // Continuous 24h Bar with rounded inner corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RevolutSecondary)
                ) {
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

                    // Vertical White Needle with glowing indicator for Current Time
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

                // Hour Markers (00, 04, 08, 12, 16, 20, 24)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { hourStr ->
                        Text(
                            text = hourStr,
                            color = RevolutTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (stats != null) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Bento Grid: Two balanced stat tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tile 1: Hours ON
                        BentoStatTile(
                            modifier = Modifier.weight(1f),
                            icon = "⚡",
                            title = "Зі світлом",
                            value = String.format(Locale.US, "%.1f", stats.hoursOn),
                            unit = "год",
                            color = PowerOn
                        )

                        // Tile 2: Hours OFF
                        BentoStatTile(
                            modifier = Modifier.weight(1f),
                            icon = "🌑",
                            title = "Без світла",
                            value = String.format(Locale.US, "%.1f", stats.hoursOff),
                            unit = "год",
                            color = PowerOff
                        )
                    }

                    if (stats.hoursSwitching > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PowerSwitching.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PowerSwitching.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "⚠️", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f год можливих відключень (жовті слоти)", stats.hoursSwitching),
                                    color = PowerSwitching,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Smart Planner Bento Card
                    if (longestWindow != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = RevolutSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorderBrush, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PowerOn.copy(alpha = 0.15f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "💡", fontSize = 18.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "НАЙДОВШЕ ВІКНО ЗІ СВІТЛОМ",
                                        color = PowerOn,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%02d:%02d – %02d:%02d  •  %s",
                                            longestWindow.startHour, longestWindow.startMinute,
                                            longestWindow.endHour, longestWindow.endMinute,
                                            formatDuration(longestWindow.durationMinutes)
                                        ),
                                        color = RevolutTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Оптимальний час для прання, приготування та зарядки пристроїв",
                                        color = RevolutTextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoStatTile(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    value: String,
    unit: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = RevolutSecondary,
        modifier = modifier.border(1.dp, CardBorderBrush, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = title, color = RevolutTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(text = icon, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = color,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    color = RevolutTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

// ====================================================================
// INTERVALS LIST CARD (Clean Step-Timeline)
// ====================================================================
@Composable
private fun IntervalsCard(
    blocks: List<ScheduleBlock>,
    currentTotalMinutes: Int,
    showPastSlots: Boolean,
    onToggleShowPast: () -> Unit
) {
    if (blocks.isEmpty()) return

    val (pastBlocks, activeOrFutureBlocks) = if (currentTotalMinutes >= 0) {
        blocks.partition { (it.endHour * 60 + it.endMinute) <= currentTotalMinutes }
    } else {
        Pair(emptyList(), blocks)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, brush = CardBorderBrush, shape = RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = RevolutCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "РОЗКЛАД ІНТЕРВАЛІВ",
                    color = RevolutTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                if (pastBlocks.isNotEmpty()) {
                    Text(
                        text = if (showPastSlots) "Сховати минулі" else "Минулі (${pastBlocks.size})",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleShowPast() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Past intervals (smooth collapsible transition)
            AnimatedVisibility(
                visible = showPastSlots,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pastBlocks.forEach { block ->
                        IntervalRow(block = block, isCurrent = false, isPast = true)
                    }
                    HorizontalDivider(
                        color = RevolutCardBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
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
        SlotStatus.SWITCHING -> Pair(PowerSwitching, "Можливе перемикання")
        SlotStatus.UNKNOWN -> Pair(PowerUnknown, "Невідомо")
    }

    val alpha = if (isPast) 0.4f else 1.0f
    val bg = if (isCurrent) color.copy(alpha = 0.10f) else Color.Transparent
    val borderModifier = if (isCurrent) {
        Modifier.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .then(borderModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded status notch
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(modifier = Modifier.width(12.dp))

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
                            color = color.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = "ЗАРАЗ",
                                color = color,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
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
// VPN / REGIONAL ERROR BANNER
// ====================================================================
@Composable
private fun VpnErrorBanner(
    errorMessage: String,
    hasCachedData: Boolean,
    lastCheckTime: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF22170B)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚠️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Джерело недоступне (VPN/Регіон)",
                        color = Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRetry() }
                ) {
                    Text(
                        text = "Повторити",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = if (hasCachedData) {
                    "Сайт обленерго блокує іноземні IP. Показано останній збережений графік ($lastCheckTime)."
                } else {
                    "Не вдалося з'єднатися з poe.pl.ua. Якщо увімкнено VPN — вимкніть його для синхронізації."
                },
                color = RevolutTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// ====================================================================
// EMPTY STATE FOR TOMORROW
// ====================================================================
@Composable
private fun EmptyTomorrowCard(
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, brush = CardBorderBrush, shape = RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = RevolutCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⏳",
                fontSize = 42.sp
            )
            Text(
                text = "Графік ще готується",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RevolutTextPrimary
            )
            Text(
                text = "Полтаваобленерго зазвичай публікує графік на завтра у другій половині дня (після 18:00–20:00).",
                fontSize = 13.sp,
                color = RevolutTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentBlue.copy(alpha = 0.15f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onRefresh() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔄", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLoading) "Перевірка..." else "Оновити графік",
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
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
        color = Color(0xFF260D13)
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
// QUEUE SELECTION DIALOG: Clean 12-queue selector
// ====================================================================
@Composable
private fun QueueSelectionDialog(
    currentQueueNumber: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Графіки погодинних відключень (ГПВ)",
                    color = RevolutTextSecondary,
                    fontSize = 12.sp
                )

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
                            onClick = { onSelect(q1) }
                        )
                        QueueButton(
                            queueNumber = q2,
                            isSelected = currentQueueNumber == q2,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(q2) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Закрити", color = PowerOn, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) PowerOn.copy(alpha = 0.18f) else RevolutSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (isSelected) PowerOn else RevolutCardBorder,
                RoundedCornerShape(14.dp)
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
