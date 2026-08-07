package com.spen.canvas

import com.spen.canvas.model.AppSettings
import com.spen.canvas.model.CanvasStyle
import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {

    @Test
    fun charcoalCanvasUsesLightInkForContrast() {
        val s = AppSettings(canvasStyle = CanvasStyle.CHARCOAL)
        assertFalse(s.isLightCanvas)
        assertEquals(0xFF0F172A, s.canvasColor)
        // Default ink must contrast the dark surface (the old bug wrote near-invisible dark ink).
        assertEquals(0xFFF8FAFC, s.defaultInk)
    }

    @Test
    fun paperCanvasUsesDarkInkForContrast() {
        val s = AppSettings(canvasStyle = CanvasStyle.PAPER)
        assertTrue(s.isLightCanvas)
        assertEquals(0xFF1E293B, s.defaultInk)
    }

    @Test
    fun oledCanvasIsTrueBlackWithLightInk() {
        val s = AppSettings(canvasStyle = CanvasStyle.OLED)
        assertEquals(0xFF000000, s.canvasColor)
        assertFalse(s.isLightCanvas)
        assertEquals(0xFFF8FAFC, s.defaultInk)
    }

    @Test
    fun patternColorFlipsWithSurfaceBrightness() {
        assertNotEquals(
            AppSettings(canvasStyle = CanvasStyle.PAPER).patternColor,
            AppSettings(canvasStyle = CanvasStyle.CHARCOAL).patternColor
        )
    }

    @Test
    fun fingerPanIsDefaultPenFirstBehavior() {
        // Off means one finger pans the canvas — the S Pen-first default.
        assertFalse(AppSettings().drawWithFinger)
    }
}
