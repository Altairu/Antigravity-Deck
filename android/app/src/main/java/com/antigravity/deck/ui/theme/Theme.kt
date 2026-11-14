package com.antigravity.deck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// アプリ内のテーマモード設定
enum class AppThemeMode(val wireValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun resolveDark(isSystemDark: Boolean): Boolean {
        return when (this) {
            SYSTEM -> isSystemDark
            LIGHT -> false
            DARK -> true
        }
    }

    companion object {
        fun parse(raw: String?): AppThemeMode {
            return entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: SYSTEM
        }
    }
}

@Immutable
data class DeckPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val borderMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val statusIdle: Color,
    val statusThinking: Color,
    val statusApproval: Color,
    val statusDone: Color,
    val statusError: Color,
    val approveGreen: Color,
    val rejectRed: Color,
    val accentBlue: Color,
    val codeBackground: Color,
    val codeText: Color,
    val inlineCodeBackground: Color,
    val inlineCodeText: Color,
    val quoteBackground: Color,
    val approvalBannerBackground: Color
)

val DarkDeckPalette = DeckPalette(
    isDark = true,
    background = Color(0xFF111318),
    surface = Color(0xFF1A1D24),
    surfaceElevated = Color(0xFF232732),
    borderMuted = Color(0xFF2E3440),
    textPrimary = Color(0xFFECEFF4),
    textSecondary = Color(0xFF9AA4B2),
    statusIdle = Color(0xFF7B8496),
    statusThinking = Color(0xFF56B6C2),
    statusApproval = Color(0xFFE5A93B),
    statusDone = Color(0xFF4EC990),
    statusError = Color(0xFFE06C75),
    approveGreen = Color(0xFF10B981),
    rejectRed = Color(0xFFEF4444),
    accentBlue = Color(0xFF3B82F6),
    codeBackground = Color(0xFF151820),
    codeText = Color(0xFFE2E8F0),
    inlineCodeBackground = Color(0xFF252B37),
    inlineCodeText = Color(0xFF7DD3FC),
    quoteBackground = Color(0xFF161A22),
    approvalBannerBackground = Color(0xFF2A2212)
)

val LightDeckPalette = DeckPalette(
    isDark = false,
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFECEFF4),
    borderMuted = Color(0xFFE0E5EC),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    statusIdle = Color(0xFF6B7280),
    statusThinking = Color(0xFF0284C7),
    statusApproval = Color(0xFFD97706),
    statusDone = Color(0xFF059669),
    statusError = Color(0xFFDC2626),
    approveGreen = Color(0xFF059669),
    rejectRed = Color(0xFFDC2626),
    accentBlue = Color(0xFF2563EB),
    codeBackground = Color(0xFFF1F5F9),
    codeText = Color(0xFF1E293B),
    inlineCodeBackground = Color(0xFFE2E8F0),
    inlineCodeText = Color(0xFF0369A1),
    quoteBackground = Color(0xFFF1F5F9),
    approvalBannerBackground = Color(0xFFFEF3C7)
)

val LocalDeckColors = staticCompositionLocalOf { DarkDeckPalette }

// Compose内から現在のテーマ配色へアクセスするためのファサード
object DeckColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.surface
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.surfaceElevated
    val BorderMuted: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.borderMuted
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.textSecondary
    val StatusIdle: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.statusIdle
    val StatusThinking: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.statusThinking
    val StatusApproval: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.statusApproval
    val StatusDone: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.statusDone
    val StatusError: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.statusError
    val ApproveGreen: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.approveGreen
    val RejectRed: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.rejectRed
    val AccentBlue: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.accentBlue
    val CodeBackground: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.codeBackground
    val CodeText: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.codeText
    val InlineCodeBackground: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.inlineCodeBackground
    val InlineCodeText: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.inlineCodeText
    val QuoteBackground: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.quoteBackground
    val ApprovalBannerBackground: Color @Composable @ReadOnlyComposable get() = LocalDeckColors.current.approvalBannerBackground
}

private fun buildColorScheme(palette: DeckPalette) = if (palette.isDark) {
    darkColorScheme(
        primary = palette.accentBlue,
        onPrimary = Color.White,
        secondary = palette.statusApproval,
        onSecondary = Color(0xFF261900),
        tertiary = palette.statusDone,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        error = palette.statusError,
        onError = Color.White,
        outline = palette.borderMuted
    )
} else {
    lightColorScheme(
        primary = palette.accentBlue,
        onPrimary = Color.White,
        secondary = palette.statusApproval,
        onSecondary = Color.White,
        tertiary = palette.statusDone,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        error = palette.statusError,
        onError = Color.White,
        outline = palette.borderMuted
    )
}

private val DeckTypography = Typography(
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
)

@Composable
fun AntigravityDeckTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val palette = if (themeMode.resolveDark(systemDark)) DarkDeckPalette else LightDeckPalette

    CompositionLocalProvider(LocalDeckColors provides palette) {
        MaterialTheme(
            colorScheme = buildColorScheme(palette),
            typography = DeckTypography,
            content = content
        )
    }
}
