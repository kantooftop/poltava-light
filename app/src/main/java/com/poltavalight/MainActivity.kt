package com.poltavalight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.poltavalight.data.CheckResult
import com.poltavalight.data.ScheduleRepository
import com.poltavalight.ui.MainScreen
import com.poltavalight.ui.theme.PoltavaLightTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private lateinit var repository: ScheduleRepository

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — we proceed either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = ScheduleRepository(this)

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
                    color = MaterialTheme.colorScheme.background
                ) {
                    var checkResult by remember { mutableStateOf<CheckResult?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    var lastCheckTime by remember { mutableStateOf(repository.getLastCheckTime()) }

                    // Load data
                    val scope = rememberCoroutineScope()

                    fun refresh() {
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            val result = repository.performCheck()
                            // Save state for change detection
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
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        refresh()
                    }

                    MainScreen(
                        checkResult = checkResult,
                        lastCheckTime = lastCheckTime,
                        isLoading = isLoading,
                        onRefresh = { refresh() }
                    )
                }
            }
        }
    }

    /**
     * Ask Android to exempt this app from battery optimization.
     * On Pixel this shows a system dialog; the user just taps "Allow".
     */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                // Some devices may not support this intent
            }
        }
    }
}

