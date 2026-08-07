package com.spen.canvas.model

import android.graphics.Path
import android.graphics.RectF
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min


enum class ActiveTool {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    SHAPE,
    TEXT,
    IMAGE
}


enum class EraserMode {
    PRECISION,
    STROKE
}

enum class ShapeType {
    LINE,
    ARROW,
    RECTANGLE,
    CIRCLE,
    TRIANGLE,
    DIAMOND
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
    val isLocked: Boolean = false,
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
        if (isLocked) return false
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
 * Geometric shape element (Line, Arrow, Rectangle, Circle, Triangle, Diamond).
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
    val isLocked: Boolean = false,
    val bounds: RectF = computeBounds(startX, startY, endX, endY, strokeWidth)
) {
    fun containsPoint(x: Float, y: Float, threshold: Float = 18f): Boolean {
        if (isLocked) return false
        val totalRadius = threshold + strokeWidth / 2f
        return when (shapeType) {
            ShapeType.LINE, ShapeType.ARROW -> {
                distanceToSegment(x, y, startX, startY, endX, endY) <= totalRadius
            }
            ShapeType.RECTANGLE, ShapeType.CIRCLE, ShapeType.TRIANGLE, ShapeType.DIAMOND -> {
                val expanded = RectF(bounds.left - totalRadius, bounds.top - totalRadius, bounds.right + totalRadius, bounds.bottom + totalRadius)
                expanded.contains(x, y)
            }
        }
    }

    companion object {
        fun computeBounds(startX: Float, startY: Float, endX: Float, endY: Float, strokeWidth: Float): RectF {
            val pad = max(strokeWidth, 12f)
            return RectF(
                min(startX, endX) - pad,
                min(startY, endY) - pad,
                max(startX, endX) + pad,
                max(startY, endY) + pad
            )
        }

        private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)
            val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
            val clampedT = t.coerceIn(0f, 1f)
            val projX = x1 + clampedT * dx
            val projY = y1 + clampedT * dy
            return hypot(px - projX, py - projY)
        }
    }
}

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
    val isLocked: Boolean = false,
    val bounds: RectF = RectF(x, y - fontSize, x + max(text.length * fontSize * 0.6f, 40f), y + fontSize * 0.4f)
) {
    fun containsPoint(px: Float, py: Float, threshold: Float = 18f): Boolean {
        if (isLocked) return false
        val expanded = RectF(bounds.left - threshold, bounds.top - threshold, bounds.right + threshold, bounds.bottom + threshold)
        return expanded.contains(px, py)
    }
}

/**
 * First-class editable Image element on canvas.
 */
data class ImageElement(
    val id: String = UUID.randomUUID().toString(),
    val localPath: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
    val isLocked: Boolean = false,
    val bounds: RectF = computeBounds(x, y, width, height)
) {
    fun containsPoint(px: Float, py: Float, threshold: Float = 15f): Boolean {
        if (isLocked) return false
        val expanded = RectF(bounds.left - threshold, bounds.top - threshold, bounds.right + threshold, bounds.bottom + threshold)
        return expanded.contains(px, py)
    }

    companion object {
        fun computeBounds(x: Float, y: Float, width: Float, height: Float): RectF {
            return RectF(x, y, x + width, y + height)
        }
    }
}



