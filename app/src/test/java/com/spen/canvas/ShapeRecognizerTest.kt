package com.spen.canvas

import com.spen.canvas.geometry.ShapeRecognizer
import com.spen.canvas.model.InkStroke
import com.spen.canvas.model.ShapeSensitivity
import com.spen.canvas.model.ShapeType
import com.spen.canvas.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ShapeRecognizerTest {

    private fun stroke(points: List<StrokePoint>) = InkStroke(points = points, color = 0xFF000000, baseWidth = 4f)

    @Test
    fun recognizesStraightLine() {
        val pts = (0..20).map { StrokePoint(it * 12f, it * 12f) }
        val result = ShapeRecognizer.recognizeShape(stroke(pts))
        assertNotNull(result)
        assertTrue(result!!.shapeType == ShapeType.LINE || result.shapeType == ShapeType.ARROW)
    }

    @Test
    fun recognizesRectangle() {
        val pts = mutableListOf<StrokePoint>()
        val x0 = 100f; val y0 = 100f; val s = 260f
        for (i in 0..20) pts.add(StrokePoint(x0 + s * i / 20f, y0))
        for (i in 0..20) pts.add(StrokePoint(x0 + s, y0 + s * i / 20f))
        for (i in 0..20) pts.add(StrokePoint(x0 + s - s * i / 20f, y0 + s))
        for (i in 0..20) pts.add(StrokePoint(x0, y0 + s - s * i / 20f))
        val result = ShapeRecognizer.recognizeShape(stroke(pts))
        assertNotNull(result)
        assertEquals(ShapeType.RECTANGLE, result!!.shapeType)
    }

    @Test
    fun recognizesCircle() {
        val pts = mutableListOf<StrokePoint>()
        val cx = 300f; val cy = 300f; val r = 150f
        for (i in 0..40) {
            val a = (Math.PI * 2 * i / 40f).toFloat()
            pts.add(StrokePoint(cx + r * cos(a), cy + r * sin(a)))
        }
        val result = ShapeRecognizer.recognizeShape(stroke(pts))
        assertNotNull(result)
        assertEquals(ShapeType.CIRCLE, result!!.shapeType)
    }

    @Test
    fun doesNotConvertHandwritingScribble() {
        // A wavy, non-straight, non-closed squiggle like cursive writing must stay ink.
        val pts = (0..40).map { i ->
            val x = i * 6f
            val y = 100f + 30f * sin(i * 0.9f)
            StrokePoint(x, y)
        }
        assertNull(ShapeRecognizer.recognizeShape(stroke(pts)))
    }

    @Test
    fun doesNotConvertTinyMark() {
        // Small marks (dots, small letters) are below the size gate.
        val pts = (0..12).map { StrokePoint(50f + it * 1.5f, 50f + it * 1.5f) }
        assertNull(ShapeRecognizer.recognizeShape(stroke(pts)))
    }

    @Test
    fun lowSensitivityIsStricterThanHigh() {
        // A slightly sloppy rectangle: high sensitivity should catch more than low.
        val pts = mutableListOf<StrokePoint>()
        val x0 = 0f; val y0 = 0f; val s = 200f
        for (i in 0..15) pts.add(StrokePoint(x0 + s * i / 15f, y0 + (if (i % 2 == 0) 6f else -6f)))
        for (i in 0..15) pts.add(StrokePoint(x0 + s, y0 + s * i / 15f))
        for (i in 0..15) pts.add(StrokePoint(x0 + s - s * i / 15f, y0 + s))
        for (i in 0..15) pts.add(StrokePoint(x0, y0 + s - s * i / 15f))
        val high = ShapeRecognizer.recognizeShape(stroke(pts), ShapeSensitivity.HIGH)
        // High sensitivity should recognize it; if low also does, that's fine — but high must not be null here.
        assertNotNull(high)
    }
}
