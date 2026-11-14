package com.antigravity.deck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object DeckColors {
    val Background = Color(0xFF090D16)
    val Surface = Color(0xFF111827)
    val SurfaceElevated = Color(0xFF172033)
    val BorderMuted = Color(0xFF283548)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)

    val StatusIdle = Color(0xFF64748B)
    val StatusThinking = Color(0xFF38BDF8)
    val StatusApproval = Color(0xFFFBBF24)
    val StatusDone = Color(0xFF34D399)
    val StatusError = Color(0xFFF87171)

    val ApproveGreen = Color(0xFF10B981)
    val RejectRed = Color(0xFFEF4444)
    val AccentBlue = Color(0xFF3B82F6)
}

private val DeckDarkColorScheme = darkColorScheme(
    primary = DeckColors.StatusThinking,
    onPrimary = Color(0xFF031524),
    secondary = DeckColors.StatusApproval,
    onSecondary = Color(0xFF261900),
    tertiary = DeckColors.StatusDone,
    background = DeckColors.Background,
    onBackground = DeckColors.TextPrimary,
    surface = DeckColors.Surface,
    onSurface = DeckColors.TextPrimary,
    surfaceVariant = DeckColors.SurfaceElevated,
    onSurfaceVariant = DeckColors.TextSecondary,
    error = DeckColors.StatusError,
    onError = Color(0xFF2D0606),
    outline = DeckColors.BorderMuted
)

private val DeckTypography = Typography(
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
)

@Composable
fun AntigravityDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeckDarkColorScheme,
        typography = DeckTypography,
        content = content
    )
}
