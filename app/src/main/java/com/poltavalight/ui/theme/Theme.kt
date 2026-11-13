package com.poltavalight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Revolut Dark Palette
val RevolutBlack = Color(0xFF000000)
val RevolutCard = Color(0xFF161618)
val RevolutCardBorder = Color(0x12FFFFFF) // 7% white border
val RevolutSecondary = Color(0xFF1C1C1E)
val RevolutTextPrimary = Color(0xFFFFFFFF)
val RevolutTextSecondary = Color(0xFF8E8E93)

// Status Accents
val PowerOn = Color(0xFF00D180)             // Mint green
val PowerOnContainer = Color(0x2600D180)
val PowerOff = Color(0xFFFF3B5C)            // Coral red
val PowerOffContainer = Color(0x26FF3B5C)
val PowerSwitching = Color(0xFFFFB800)      // Warm amber
val PowerSwitchingContainer = Color(0x26FFB800)
val PowerUnknown = Color(0xFF8E8E93)

private val RevolutDarkColorScheme = darkColorScheme(
    primary = PowerOn,
    onPrimary = Color.Black,
    primaryContainer = RevolutSecondary,
    onPrimaryContainer = RevolutTextPrimary,
    secondary = RevolutSecondary,
    onSecondary = RevolutTextPrimary,
    secondaryContainer = RevolutSecondary,
    onSecondaryContainer = RevolutTextPrimary,
    background = RevolutBlack,
    onBackground = RevolutTextPrimary,
    surface = RevolutCard,
    onSurface = RevolutTextPrimary,
    surfaceVariant = RevolutSecondary,
    onSurfaceVariant = RevolutTextSecondary,
    outline = RevolutCardBorder
)

@Composable
fun PoltavaLightTheme(
    content: @Composable () -> Unit
) {
    // Pure Revolut Dark Mode — disconnected from system dynamic colors (Material You)
    // to avoid any brownish/tinted palette.
    MaterialTheme(
        colorScheme = RevolutDarkColorScheme,
        typography = Typography(),
        content = content
    )
}
