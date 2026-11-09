package com.poltavalight.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PowerOn = Color(0xFF4CAF50)
val PowerOff = Color(0xFFF44336)
val PowerSwitching = Color(0xFFFF9800)
val EmergencyBg = Color(0xFFFFCDD2)
val PrimaryBlue = Color(0xFF1565C0)
val LightBlue = Color(0xFFE3F2FD)
val DarkText = Color(0xFF212121)
val SubText = Color(0xFF757575)
val CardBg = Color(0xFFF5F5F5)
val CurrentSlotBg = Color(0xFFBBDEFB)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightBlue,
    secondary = PowerOn,
    error = PowerOff,
    background = Color.White,
    surface = Color.White,
    onBackground = DarkText,
    onSurface = DarkText,
)

@Composable
fun PoltavaLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
