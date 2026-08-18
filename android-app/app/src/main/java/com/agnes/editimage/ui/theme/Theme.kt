package com.agnes.editimage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette taken from the reference screenshots.
val AppBackground = Color(0xFF121212)
val CardBackground = Color(0xFF1E1E1E)
val CardBackground2 = Color(0xFF2A2A2E)
val UserBubble = Color(0xFF8184FD)
val TealBadge = Color(0xFF2DD4BF)
val TextPrimary = Color(0xFFF5F5F5)
val TextMuted = Color(0xFF9E9E9E)
val SparkleCyan = Color(0xFF8AD8F0)
val ErrorRed = Color(0xFFEF6A6A)

private val DarkColors = darkColorScheme(
    primary = UserBubble,
    onPrimary = Color.White,
    secondary = TealBadge,
    onSecondary = Color(0xFF00332E),
    tertiary = SparkleCyan,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground2,
    onSurfaceVariant = TextMuted,
)

@Composable
fun AgnesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
