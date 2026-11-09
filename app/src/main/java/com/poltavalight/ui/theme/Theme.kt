package com.poltavalight.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val PowerOn = Color(0xFF2E7D32)
val PowerOnContainer = Color(0xFFC8E6C9)
val PowerOff = Color(0xFFC62828)
val PowerOffContainer = Color(0xFFFFCDD2)
val PowerSwitching = Color(0xFFE65100)
val PowerSwitchingContainer = Color(0xFFFFE0B2)

val DarkPowerOn = Color(0xFF81C784)
val DarkPowerOnContainer = Color(0xFF1B5E20)
val DarkPowerOff = Color(0xFFE57373)
val DarkPowerOffContainer = Color(0xFFB71C1C)
val DarkPowerSwitching = Color(0xFFFFB74D)
val DarkPowerSwitchingContainer = Color(0xFFBF360C)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00639B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCEE5FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF51606F),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF97CBFF),
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF004A76),
    onPrimaryContainer = Color(0xFFCEE5FF),
    secondary = Color(0xFFB9C8DA),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF)
)

@Composable
fun PoltavaLightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
