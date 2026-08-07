package com.spen.canvas.geometry

import com.spen.canvas.model.InkStroke
import com.spen.canvas.model.ShapeElement
import com.spen.canvas.model.ShapeType
import com.spen.canvas.model.StrokePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Geometry Shape Recognizer supporting "Draw & Hold" auto-straightening.
 * Uses pure Kotlin math for high performance and local unit test compatibility.
 */
object ShapeRecognizer {

    fun recognizeShape(stroke: InkStroke): ShapeElement? {
        val pts = stroke.points
        if (pts.size < 5) return null

        val start = pts.first()
        val end = pts.last()

        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val width = abs(maxX - minX)
        val height = abs(maxY - minY)

        val totalPathLength = calculatePathLength(pts)
        val directDist = distance(start.x, start.y, end.x, end.y)

        // 1. Closed shapes (Start and End are near each other)
        val closeThreshold = max(width, height) * 0.35f
        if (directDist < closeThreshold || distance(start.x, start.y, end.x, end.y) < 40f) {
            val isCircle = isCircular(pts, minX, minY, maxX, maxY)
            val shapeType = if (isCircle) ShapeType.CIRCLE else ShapeType.RECTANGLE

            return ShapeElement(
                shapeType = shapeType,
                startX = minX,
                startY = minY,
                endX = maxX,
                endY = maxY,
                color = stroke.color,
                strokeWidth = stroke.baseWidth
            )
        }

        // 2. Open shapes: Straight Line vs Arrow
        val linearity = if (totalPathLength > 0f) directDist / totalPathLength else 0f
        if (linearity > 0.80f) {
            val isArrow = hasArrowHead(pts)
            val shapeType = if (isArrow) ShapeType.ARROW else ShapeType.LINE

            return ShapeElement(
                shapeType = shapeType,
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
                color = stroke.color,
                strokeWidth = stroke.baseWidth
            )
        }

        return null
    }

    private fun calculatePathLength(points: List<StrokePoint>): Float {
        var len = 0f
        for (i in 0 until points.size - 1) {
            len += distance(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y)
        }
        return len
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun isCircular(
        points: List<StrokePoint>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float
    ): Boolean {
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val targetRadius = ((maxX - minX) + (maxY - minY)) / 4f

        if (targetRadius < 10f) return false

        var radiusVariance = 0f
        for (pt in points) {
            val r = distance(pt.x, pt.y, centerX, centerY)
            radiusVariance += abs(r - targetRadius)
        }
        val avgVariance = radiusVariance / points.size
        return (avgVariance / targetRadius) < 0.35f
    }

    private fun hasArrowHead(points: List<StrokePoint>): Boolean {
        if (points.size < 8) return false
        val end = points.last()
        val n = points.size
        val p2 = points[n - 5]

        val mainAngle = atan2(end.y - points.first().y, end.x - points.first().x)
        val tailAngle = atan2(end.y - p2.y, end.x - p2.x)

        val diff = abs(mainAngle - tailAngle)
        return diff > 0.4f
    }
}
