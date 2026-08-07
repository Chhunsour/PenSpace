package com.spen.canvas

import com.spen.canvas.geometry.ShapeRecognizer
import com.spen.canvas.model.*
import org.junit.Assert.*
import org.junit.Test

class StrokeModelTest {

    @Test
    fun testStrokePointDataModel() {
        val pt = StrokePoint(x = 100.5f, y = 200.25f, pressure = 0.85f)
        assertEquals(100.5f, pt.x, 0.001f)
        assertEquals(200.25f, pt.y, 0.001f)
        assertEquals(0.85f, pt.pressure, 0.001f)
    }

    @Test
    fun testPenConfigDefaultsAndPalette() {
        val config = PenConfig()
        assertEquals(ToolMode.PEN, config.toolMode)
        assertEquals(6f, config.baseWidth, 0.001f)
        assertTrue(PenConfig.DEFAULT_PALETTE.isNotEmpty())
        assertTrue(PenConfig.THICKNESS_PRESETS.isNotEmpty())
    }

    @Test
    fun testInkStrokeAndHighlighter() {
        val stroke = InkStroke(
            points = listOf(StrokePoint(10f, 10f), StrokePoint(50f, 50f)),
            color = 0xFF3B82F6,
            baseWidth = 6f
        )
        assertFalse(stroke.isHighlighter)
        assertTrue(stroke.containsPoint(12f, 12f, 10f))

        val highlighter = InkStroke(
            points = listOf(StrokePoint(10f, 10f), StrokePoint(50f, 50f)),
            color = 0xFFFACC15,
            alpha = 0.45f,
            baseWidth = 24f,
            isHighlighter = true
        )
        assertTrue(highlighter.isHighlighter)
        assertEquals(0.45f, highlighter.alpha, 0.001f)
    }

    @Test
    fun testShapeRecognizerStraightLine() {
        val points = mutableListOf<StrokePoint>()
        for (i in 0..20) {
            points.add(StrokePoint(i * 10f, i * 10f))
        }
        val stroke = InkStroke(points = points, color = 0xFFFFFFFF, baseWidth = 4f)
        val recognized = ShapeRecognizer.recognizeShape(stroke)

        assertNotNull(recognized)
        assertEquals(ShapeType.LINE, recognized?.shapeType)
    }

    @Test
    fun testWorldToScreenCoordinateTransformationMath() {
        val zoomScale = 2.0f
        val panX = 100f
        val panY = 50f

        val worldX = 50f
        val worldY = 30f

        val screenX = worldX * zoomScale + panX
        val screenY = worldY * zoomScale + panY

        assertEquals(200f, screenX, 0.001f)
        assertEquals(110f, screenY, 0.001f)

        val unmappedWorldX = (screenX - panX) / zoomScale
        val unmappedWorldY = (screenY - panY) / zoomScale

        assertEquals(worldX, unmappedWorldX, 0.001f)
        assertEquals(worldY, unmappedWorldY, 0.001f)
    }
}
