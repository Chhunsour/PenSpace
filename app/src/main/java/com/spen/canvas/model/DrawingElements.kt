package com.spen.canvas.model

import android.graphics.Path
import android.graphics.RectF
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class ActiveTool {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    SHAPE,
    TEXT
}

enum class EraserMode {
    PRECISION,
    STROKE
}

enum class ShapeType {
    LINE,
    ARROW,
    RECTANGLE,
    CIRCLE
}

enum class BackgroundType {
    PLAIN,
    DOTS,
    GRID,
    LINES
}

/**
 * Freehand ink stroke (Pen or Highlighter).
 */
data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Long,
    val alpha: Float = 1.0f,
    val baseWidth: Float,
    val isHighlighter: Boolean = false,
    val isEraser: Boolean = false,
    val bounds: RectF = computeBounds(points)
) {
    fun createPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            val pt = points[0]
            path.addCircle(pt.x, pt.y, max((baseWidth * pt.pressure), 1f) / 2f, Path.Direction.CW)
            return path
        }

        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val midX = (p0.x + p1.x) / 2f
            val midY = (p0.y + p1.y) / 2f
            path.quadTo(p0.x, p0.y, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x, last.y)

        return path
    }

    fun containsPoint(x: Float, y: Float, threshold: Float = 15f): Boolean {
        for (pt in points) {
            val dx = pt.x - x
            val dy = pt.y - y
            if (dx * dx + dy * dy <= threshold * threshold) {
                return true
            }
        }
        return false
    }

    companion object {
        fun computeBounds(points: List<StrokePoint>): RectF {
            if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)
            var minX = points[0].x
            var minY = points[0].y
            var maxX = points[0].x
            var maxY = points[0].y

            for (i in 1 until points.size) {
                val pt = points[i]
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
            return RectF(minX, minY, maxX, maxY)
        }
    }
}

/**
 * Geometric shape element (Line, Arrow, Rectangle, Circle).
 */
data class ShapeElement(
    val id: String = UUID.randomUUID().toString(),
    val shapeType: ShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Long,
    val strokeWidth: Float,
    val bounds: RectF = RectF(
        min(startX, endX),
        min(startY, endY),
        max(startX, endX),
        max(startY, endY)
    )
)

/**
 * Typed text element on canvas.
 */
data class TextElement(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float = 22f,
    val color: Long = 0xFFF8FAFC,
    val bounds: RectF = RectF(x, y - fontSize, x + text.length * fontSize * 0.6f, y + fontSize * 0.3f)
)
