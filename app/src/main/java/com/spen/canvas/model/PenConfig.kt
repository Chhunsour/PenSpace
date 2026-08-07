package com.spen.canvas.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ToolMode {
    PEN,
    ERASER
}

/**
 * Configuration settings for the active drawing tool.
 *
 * @property toolMode Mode of operation (PEN or ERASER).
 * @property baseWidth Nominal width of the pen tip in density-independent points/pixels.
 * @property color Color of the ink in ARGB format.
 * @property minPressureRatio Scaling multiplier for minimum pressure.
 * @property maxPressureRatio Scaling multiplier for maximum pressure.
 */
data class PenConfig(
    val toolMode: ToolMode = ToolMode.PEN,
    val baseWidth: Float = 6f,
    val color: Long = 0xFF1E293B, // Dark slate ink default
    val minPressureRatio: Float = 0.3f,
    val maxPressureRatio: Float = 2.2f
) {
    fun getEffectiveColor(): Int = if (toolMode == ToolMode.ERASER) {
        0x00000000 // Transparent for clear / eraser compositing
    } else {
        color.toInt()
    }

    companion object {
        val DEFAULT_PALETTE = listOf(
            0xFF0F172A, // Ink Black
            0xFF1E40AF, // Deep Royal Blue
            0xFFB91C1C, // Crimson Red
            0xFF047857, // Emerald Green
            0xFF6B21A8, // Deep Purple
            0xFFD97706, // Amber Gold
            0xFFF8FAFC  // Bright White (for dark mode)
        )

        val THICKNESS_PRESETS = listOf(
            2f to "Fine",
            6f to "Medium",
            12f to "Bold"
        )
    }
}
