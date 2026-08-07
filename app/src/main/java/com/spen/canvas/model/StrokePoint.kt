package com.spen.canvas.model

/**
 * High-precision sample point within a stylus ink stroke.
 *
 * @property x Horizontal canvas coordinate in pixels.
 * @property y Vertical canvas coordinate in pixels.
 * @property pressure Stylus tip normalized pressure value (typically 0.0f .. 1.0f+).
 * @property timestamp Event timestamp in milliseconds.
 * @property tilt Stylus tilt angle in radians (if reported by hardware).
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis(),
    val tilt: Float = 0f
)
