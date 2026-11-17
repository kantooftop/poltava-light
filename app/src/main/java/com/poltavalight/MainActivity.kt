package com.poltavalight

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.poltavalight.data.CheckResult
import com.poltavalight.data.ScheduleRepository
import com.poltavalight.ui.MainScreen
import com.poltavalight.ui.theme.PoltavaLightTheme
import com.poltavalight.ui.theme.RevolutBlack
import com.poltavalight.widget.LightAppWidgetProvider
import com.poltavalight.widget.SplitAppWidgetProvider
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private lateinit var repository: ScheduleRepository

    // Power connection live state
    private val isChargingState = mutableStateOf(false)

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkBatteryState()
        }
    }

    // Dynamic callback for notification permission requests
    private var onNotificationPermissionResult: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onNotificationPermissionResult?.invoke(isGranted)
        onNotificationPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Enable modern edge-to-edge with transparent system bars
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // 2. Ensure status bar and navigation bar icons remain bright/light (white) on dark theme
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        super.onCreate(savedInstanceState)

        repository = ScheduleRepository(this)
        checkBatteryState()

        // Register power connection broadcast receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(BatteryManager.ACTION_CHARGING)
        }
        registerReceiver(powerReceiver, filter)

        // Request to disable battery optimization
        requestBatteryOptimizationExemption()

        setContent {
            PoltavaLightTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RevolutBlack
                ) {
                    val initialQueue = remember { repository.getSelectedQueueNumber() }
                    var selectedQueueNumber by remember { mutableStateOf(initialQueue) }
                    var selectedQueueLabel by remember { mutableStateOf(repository.getSelectedQueueLabel()) }

                    // Instantly load cached data so launching from widget or launcher is immediate!
                    var checkResult by remember {
                        mutableStateOf<CheckResult?>(repository.getCachedCheckResult(initialQueue))
                    }
                    var isLoading by remember {
                        mutableStateOf(checkResult?.schedule == null)
                    }
                    var lastCheckTime by remember { mutableStateOf(repository.getRelativeUpdateTime()) }
                    var isNotificationsEnabled by remember { mutableStateOf(repository.isNotificationsEnabled()) }
                    val savedQueues = remember { repository.getSavedQueues() }
                    val isCharging by isChargingState

                    val scope = rememberCoroutineScope()

                    fun refresh(queue: String = selectedQueueNumber) {
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            val result = repository.performCheck(queue)
                            if (result.schedule != null) {
                                repository.saveCurrentState(
                                    result.schedule.rawHash,
                                    result.emergencies
                                )
                            }
                            withContext(Dispatchers.Main) {
                                checkResult = result
                                lastCheckTime = repository.getRelativeUpdateTime()
                                isLoading = false
                                LightAppWidgetProvider.updateAllWidgets(this@MainActivity)
                                SplitAppWidgetProvider.updateAllWidgets(this@MainActivity)
                            }
                        }
                    }

                    LaunchedEffect(selectedQueueNumber) {
                        refresh(selectedQueueNumber)
                    }

                    MainScreen(
                        checkResult = checkResult,
                        lastCheckTime = lastCheckTime,
                        isLoading = isLoading,
                        isCharging = isCharging,
                        selectedQueueNumber = selectedQueueNumber,
                        selectedQueueLabel = selectedQueueLabel,
                        savedQueues = savedQueues,
                        isNotificationsEnabled = isNotificationsEnabled,
                        onQueueSelected = { queue ->
                            selectedQueueNumber = queue.queueNumber
                            selectedQueueLabel = queue.label
                            repository.setSelectedQueue(queue.queueNumber, queue.label)

                            val cached = repository.getCachedCheckResult(queue.queueNumber)
                            if (cached.schedule != null) {
                                checkResult = cached
                            }
                            refresh(queue.queueNumber)
                        },
                        onToggleNotifications = {
                            handleNotificationToggle(isNotificationsEnabled) { newState ->
                                isNotificationsEnabled = newState
                            }
                        },
                        onRefresh = { refresh() },
                        onShare = { textToShare ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                type = "text/plain"
                            }
                            startActivity(Intent.createChooser(sendIntent, "Поділитися графіком"))
                        }
                    )
                }
            }
        }
    }

    /**
     * Handles notification toggle with permission check on Android 13+ and Toast feedback.
     */
    private fun handleNotificationToggle(
        currentEnabled: Boolean,
        onStateChanged: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                // Not granted yet: request permission first, do not toggle silently
                onNotificationPermissionResult = { granted ->
                    if (granted) {
                        repository.setNotificationsEnabled(true)
                        onStateChanged(true)
                        Toast.makeText(this, "Сповіщення увімкнено", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.setNotificationsEnabled(false)
                        onStateChanged(false)
                        Toast.makeText(this, "Потрібен дозвіл для відправки сповіщень", Toast.LENGTH_SHORT).show()
                    }
                }
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // Permission already granted or Android < 13
        val newState = !currentEnabled
        repository.setNotificationsEnabled(newState)
        onStateChanged(newState)
        val msg = if (newState) "Сповіщення увімкнено" else "Сповіщення вимкнено"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        checkBatteryState()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(powerReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun checkBatteryState() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            registerReceiver(null, filter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        isChargingState.value = isCharging
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
