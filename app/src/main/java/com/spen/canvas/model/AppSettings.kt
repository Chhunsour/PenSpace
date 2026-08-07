package com.spen.canvas.model

/**
 * How the app resolves light vs. dark appearance and chrome styling.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    WARM_PAPER,
    TRUE_BLACK,
    SOFT_GRAY
}

/**
 * Paper / canvas surface style. Independent from [ThemeMode] chrome so a user can, e.g.,
 * write on warm paper while keeping dark tool panels, or use a true-black OLED canvas.
 */
enum class CanvasStyle {
    CHARCOAL, // Dark slate infinite canvas (default, matches app chrome)
    WHITE,    // Clean white paper
    PAPER,    // Warm off-white handwriting paper
    OLED      // True black, battery friendly on the S23 Ultra AMOLED
}


/** How much geometric smoothing to apply to ink. Kept gentle so letters keep their shape. */
enum class SmoothingLevel {
    OFF,      // Raw points — most physically connected, slightly jaggy on fast curves
    STANDARD, // Midpoint quadratic smoothing (default) — natural, no distortion
    EXTRA     // One extra averaging pass — smoothest, for large/slow writing
}

/** Confidence threshold for turning a held stroke into a clean shape. */
enum class ShapeSensitivity {
    LOW,    // Only very obvious shapes snap (safest for handwriting)
    MEDIUM, // Balanced (default)
    HIGH    // Snaps looser sketches
}

/**
 * Persisted user preferences for appearance, S Pen behavior and canvas ergonomics.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val canvasStyle: CanvasStyle = CanvasStyle.CHARCOAL,
    /** When false (default) a single finger pans the canvas — pen-first. When true, finger also draws. */
    val drawWithFinger: Boolean = false,
    /** Mirror the floating tool dock toward the left for left-handed use. */
    val leftHanded: Boolean = false,
    /** Vary stroke width with S Pen tip pressure. */
    val pressureSensitivity: Boolean = true,
    /** Subtle haptic tick on tool changes and recognitions. */
    val hapticFeedback: Boolean = true,
    /** Holding the S Pen side button (or flipping to eraser tip) erases while held. */
    val sidePenButtonErases: Boolean = true,
    /** Diameter (in canvas px) of the momentary side-button eraser. Remembered across sessions. */
    val tempEraserSize: Float = 46f,
    /** Draw-and-hold auto-straightens a rough sketch into a clean shape. */
    val shapeSnapOnHold: Boolean = true,
    /** Geometric smoothing applied to freehand ink. */
    val strokeSmoothing: SmoothingLevel = SmoothingLevel.STANDARD,
    /** How eagerly held strokes snap into clean shapes. */
    val shapeSensitivity: ShapeSensitivity = ShapeSensitivity.MEDIUM,
    /** Opacity of the floating tool surfaces (0.45 very glassy .. 1.0 solid). */
    val toolbarOpacity: Float = 0.72f
) {
    /** True canvas surface color for the current style. */
    val canvasColor: Long
        get() = when (canvasStyle) {
            CanvasStyle.CHARCOAL -> 0xFF0F172A
            CanvasStyle.WHITE -> 0xFFFFFFFF
            CanvasStyle.PAPER -> 0xFFF7F3EA
            CanvasStyle.OLED -> 0xFF000000
        }

    /** Whether the canvas surface is light (drives contrast-aware ink defaults & pattern color). */
    val isLightCanvas: Boolean
        get() = canvasStyle == CanvasStyle.PAPER || canvasStyle == CanvasStyle.WHITE


    /** Grid/dot/line guide color that reads clearly on the current surface. */
    val patternColor: Long
        get() = if (isLightCanvas) 0x1A1E293B else 0x1FFFFFFF

    /** Contrast-safe default ink for the current surface. */
    val defaultInk: Long
        get() = if (isLightCanvas) 0xFF1E293B else 0xFFF8FAFC
}
