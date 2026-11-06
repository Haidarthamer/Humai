package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CompanionColorScheme = darkColorScheme(
    primary = CompanionCyan,
    onPrimary = Color(0xFF001F29),
    primaryContainer = CompanionCyanDark,
    onPrimaryContainer = CompanionCyanBright,
    secondary = CompanionBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0D2854),
    onSecondaryContainer = Color(0xFF82B1FF),
    tertiary = CompanionViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF261452),
    onTertiaryContainer = CompanionPurple,
    background = CompanionBackground,
    onBackground = CompanionTextPrimary,
    surface = CompanionSurface,
    onSurface = CompanionTextPrimary,
    surfaceVariant = CompanionSurfaceVariant,
    onSurfaceVariant = CompanionTextSecondary,
    outline = CompanionBorder,
    outlineVariant = Color(0xFF142036),
    error = CompanionError,
    onError = Color.White
)

@Composable
fun CompanionAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CompanionColorScheme,
        typography = Typography,
        content = content
    )
}
