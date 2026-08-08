package com.spen.canvas.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.spen.canvas.model.AppSettings
import com.spen.canvas.model.ThemeMode

/**
 * Resolved chrome palette for the floating tool dock, popovers, headers and dialogs.
 * Kept separate from the canvas surface color (which lives in [AppSettings]) so tool
 * panels can stay dark over a light paper canvas if desired.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val panel: Color,
    val panelBorder: Color,
    val onPanel: Color,
    val onPanelMuted: Color,
    val divider: Color,
    val accent: Color,
    val danger: Color
)

private val DarkChrome = AppColors(
    isDark = true,
    panel = Color(0xFF1E293B),
    panelBorder = Color(0xFF334155),
    onPanel = Color(0xFFF8FAFC),
    onPanelMuted = Color(0xFF94A3B8),
    divider = Color(0xFF334155),
    accent = Color(0xFF6366F1),
    danger = Color(0xFFEF4444)
)

private val LightChrome = AppColors(
    isDark = false,
    panel = Color(0xFFFFFFFF),
    panelBorder = Color(0xFFE2E8F0),
    onPanel = Color(0xFF0F172A),
    onPanelMuted = Color(0xFF64748B),
    divider = Color(0xFFE2E8F0),
    accent = Color(0xFF4F46E5),
    danger = Color(0xFFDC2626)
)

private val WarmPaperChrome = AppColors(
    isDark = false,
    panel = Color(0xF8F5EFE6),
    panelBorder = Color(0x224A3E3D),
    onPanel = Color(0xFF2D2424),
    onPanelMuted = Color(0xFF786C6A),
    divider = Color(0x1F4A3E3D),
    accent = Color(0xFFD97706),
    danger = Color(0xFFB91C1C)
)

private val TrueBlackChrome = AppColors(
    isDark = true,
    panel = Color(0xF50A0A0A),
    panelBorder = Color(0x38FFFFFF),
    onPanel = Color(0xFFFFFFFF),
    onPanelMuted = Color(0xFFA1A1AA),
    divider = Color(0x28FFFFFF),
    accent = Color(0xFF38BDF8),
    danger = Color(0xFFEF4444)
)

private val SoftGrayChrome = AppColors(
    isDark = false,
    panel = Color(0xF8F1F5F9),
    panelBorder = Color(0x180F172A),
    onPanel = Color(0xFF0F172A),
    onPanelMuted = Color(0xFF64748B),
    divider = Color(0x180F172A),
    accent = Color(0xFF0EA5E9),
    danger = Color(0xFFE11D48)
)

/** Resolve the chrome palette for the given settings and system dark state. */
fun resolveAppColors(settings: AppSettings, systemInDark: Boolean): AppColors {
    return when (settings.themeMode) {
        ThemeMode.LIGHT -> LightChrome
        ThemeMode.DARK -> DarkChrome
        ThemeMode.SYSTEM -> if (systemInDark) DarkChrome else LightChrome
        ThemeMode.WARM_PAPER -> WarmPaperChrome
        ThemeMode.TRUE_BLACK -> TrueBlackChrome
        ThemeMode.SOFT_GRAY -> SoftGrayChrome
    }
}

@Composable
fun rememberAppColors(settings: AppSettings, systemInDark: Boolean): AppColors =
    resolveAppColors(settings, systemInDark)

