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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.poltavalight.data.CheckResult
import com.poltavalight.data.SavedQueue
import com.poltavalight.data.ScheduleRepository
import com.poltavalight.ui.MainScreen
import com.poltavalight.ui.theme.PoltavaLightTheme
import com.poltavalight.ui.theme.RevolutBlack
import com.poltavalight.widget.LightAppWidgetProvider
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

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceeded */ }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request to disable battery optimization
        requestBatteryOptimizationExemption()

        setContent {
            PoltavaLightTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RevolutBlack
                ) {
                    var checkResult by remember { mutableStateOf<CheckResult?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    var lastCheckTime by remember { mutableStateOf(repository.getLastCheckTime()) }
                    var selectedQueueNumber by remember { mutableStateOf(repository.getSelectedQueueNumber()) }
                    var selectedQueueLabel by remember { mutableStateOf(repository.getSelectedQueueLabel()) }
                    var isNotificationsEnabled by remember { mutableStateOf(repository.isNotificationsEnabled()) }
                    var savedQueues by remember { mutableStateOf(repository.getSavedQueues()) }
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
                                lastCheckTime = repository.getLastCheckTime()
                                isLoading = false
                                LightAppWidgetProvider.updateAllWidgets(this@MainActivity)
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
                            refresh(queue.queueNumber)
                        },
                        onToggleNotifications = {
                            val newState = !isNotificationsEnabled
                            isNotificationsEnabled = newState
                            repository.setNotificationsEnabled(newState)
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

    /**
     * Ask Android to exempt this app from battery optimization.
     */
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
