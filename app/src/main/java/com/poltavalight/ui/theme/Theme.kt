package com.poltavalight.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ====================================================================
// PALETTE: Obsidian Slate (frontend-design & better-colors)
// ====================================================================
val RevolutBlack = Color(0xFF090A0E)
val RevolutCard = Color(0xFF13151D)
val RevolutCardBorder = Color(0x1AFFFFFF)
val RevolutCardBorderTop = Color(0x38FFFFFF)
val RevolutSecondary = Color(0xFF1A1D27)
val RevolutSurfaceHighlight = Color(0xFF232836)
val RevolutTextPrimary = Color(0xFFF8FAFC)
val RevolutTextSecondary = Color(0xFF94A3B8)
val RevolutTextMuted = Color(0xFF64748B)

// Ultra High-Chroma Status Colors (OKLCH-tuned)
val PowerOn = Color(0xFF00E599)             // Radiant Mint / Emerald
val PowerOnContainer = Color(0x2200E599)
val PowerOnGlow = Color(0x3500E599)

val PowerOff = Color(0xFFFF3366)            // Electric Coral / Rose
val PowerOffContainer = Color(0x22FF3366)
val PowerOffGlow = Color(0x35FF3366)

val PowerSwitching = Color(0xFFFFB020)      // Sun Gold / Amber
val PowerSwitchingContainer = Color(0x22FFB020)
val PowerSwitchingGlow = Color(0x35FFB020)

val PowerUnknown = Color(0xFF94A3B8)
val AccentBlue = Color(0xFF388AF6)

// Gradient Brushes (css-border-gradient)
val CardBorderBrush = Brush.verticalGradient(
    colors = listOf(Color(0x36FFFFFF), Color(0x0CFFFFFF))
)

val CardGlowGradientOn = Brush.radialGradient(
    colors = listOf(Color(0x2800E599), Color.Transparent)
)

val CardGlowGradientOff = Brush.radialGradient(
    colors = listOf(Color(0x28FF3366), Color.Transparent)
)

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
    MaterialTheme(
        colorScheme = RevolutDarkColorScheme,
        typography = Typography(),
        content = content
    )
}
