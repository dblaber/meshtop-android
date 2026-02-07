package com.meshtop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Meshtop-specific colors matching the TUI
val DirectGreen = Color(0xFF4CAF50)
val FirstHearerCyan = Color(0xFF00BCD4)
val MultiGatewayCyan = Color(0xFF80DEEA)
val RelayOrange = Color(0xFFFF9800)
val HeaderBlue = Color(0xFF42A5F5)
val StatMagenta = Color(0xFFCE93D8)
val WarningYellow = Color(0xFFFFEB3B)
val ErrorRed = Color(0xFFEF5350)
val SurfaceDark = Color(0xFF1A1A2E)
val SurfaceCard = Color(0xFF16213E)
val TextDim = Color(0xFF9E9E9E)

val Mono = FontFamily.Monospace

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF82B1FF),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFCE93D8),
    background = Color(0xFF0F0F23),
    surface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFF16213E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = ErrorRed,
)

@Composable
fun MeshtopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
