package com.dronebox.app

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

/** What the operator picked in Settings. System follows the device. */
enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

@Immutable
data class DockPalette(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val ok: Color,
    val warn: Color,
    val stop: Color,
    val motion: Color,
)

/**
 * The dock's own page is dark with green for "at a known level", amber for
 * "moving or unknown" and red for STOP. The app keeps that vocabulary in both
 * themes, so an operator who has used one reads the other without relearning
 * colours. The light theme uses deeper shades of the same hues: the dark
 * theme's pastels would not hold enough contrast on white in sunlight.
 */
val DarkPalette = DockPalette(
    bg = Color(0xFF0D0E10),
    surface = Color(0xFF15171A),
    raised = Color(0xFF1C1F23),
    line = Color(0xFF2A2E34),
    text = Color(0xFFECEDEF),
    dim = Color(0xFF8C929B),
    faint = Color(0xFF5A6068),
    ok = Color(0xFF4ADE80),
    warn = Color(0xFFFBBF24),
    stop = Color(0xFFF87171),
    motion = Color(0xFF7DD3FC),
)

val LightPalette = DockPalette(
    bg = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFF0F2F5),
    line = Color(0xFFD8DCE2),
    text = Color(0xFF14161A),
    dim = Color(0xFF5B626C),
    faint = Color(0xFF8A909A),
    ok = Color(0xFF15803D),
    warn = Color(0xFFB45309),
    stop = Color(0xFFDC2626),
    motion = Color(0xFF0369A1),
)

private val LocalDockPalette = staticCompositionLocalOf { DarkPalette }

/** The current theme's colours, read wherever the UI draws. */
object Dock {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.raised
    val Line: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.line
    val Text: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.text
    val Dim: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.dim
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.faint

    val Ok: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.ok
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.warn
    val Stop: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.stop
    val Motion: Color @Composable @ReadOnlyComposable get() = LocalDockPalette.current.motion

    val Mono = FontFamily.Monospace
}

private val type = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
    )
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun DockTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = p.ok,
        onPrimary = p.bg,
        secondary = p.motion,
        onSecondary = p.bg,
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.dim,
        outline = p.line,
        outlineVariant = p.line,
        error = p.stop,
        onError = p.bg,
    )
    CompositionLocalProvider(LocalDockPalette provides p) {
        MaterialTheme(colorScheme = colors, typography = type, content = content)
    }
}
