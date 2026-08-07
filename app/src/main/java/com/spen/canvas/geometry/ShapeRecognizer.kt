package com.spen.canvas.geometry

import com.spen.canvas.model.InkStroke
import com.spen.canvas.model.ShapeElement
import com.spen.canvas.model.ShapeSensitivity
import com.spen.canvas.model.ShapeType
import com.spen.canvas.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry shape recognizer for the "draw & hold" gesture.
 *
 * Design goals:
 *  - Never convert ordinary handwriting. Recognition only runs when the user deliberately
 *    holds the pen still at the end of a stroke, and every candidate must also pass geometric
 *    confidence checks (size, closure, linearity, corner count, radius variance).
 *  - Distinguish rectangle vs. circle by counting sharp corners rather than variance alone.
 *  - Emit editable [ShapeElement] objects, never flattened pixels.
 */
object ShapeRecognizer {

    private data class Thresholds(
        val minSize: Float,
        val closeFraction: Float,
        val linearity: Float,
        val closedFitMax: Float
    )

    private fun thresholdsFor(sensitivity: ShapeSensitivity) = when (sensitivity) {
        ShapeSensitivity.LOW -> Thresholds(minSize = 60f, closeFraction = 0.16f, linearity = 0.92f, closedFitMax = 0.20f)
        ShapeSensitivity.MEDIUM -> Thresholds(minSize = 46f, closeFraction = 0.26f, linearity = 0.86f, closedFitMax = 0.30f)
        ShapeSensitivity.HIGH -> Thresholds(minSize = 36f, closeFraction = 0.36f, linearity = 0.80f, closedFitMax = 0.42f)
    }

    fun recognizeShape(
        stroke: InkStroke,
        sensitivity: ShapeSensitivity = ShapeSensitivity.MEDIUM
    ): ShapeElement? {
        val pts = stroke.points
        if (pts.size < 6) return null

        val t = thresholdsFor(sensitivity)

        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val diag = hypot(width, height)

        // Reject tiny marks (dots, small letters) — a deliberate shape is reasonably large.
        if (max(width, height) < t.minSize) return null

        val start = pts.first()
        val end = pts.last()
        val pathLen = pathLength(pts)
        if (pathLen < t.minSize) return null

        val endGap = dist(start.x, start.y, end.x, end.y)
        val isClosed = endGap < diag * t.closeFraction

        val corners = countCorners(pts)

        fun boxShape(type: ShapeType) = ShapeElement(
            shapeType = type, startX = minX, startY = minY, endX = maxX, endY = maxY,
            color = stroke.color, strokeWidth = stroke.baseWidth
        )

        // 1) Closed shapes → circle, triangle, or rectangle depending on corners & fit error
        if (isClosed) {
            val circErr = circleFitError(pts, minX, minY, maxX, maxY)
            val rectErr = rectFitError(pts, minX, minY, maxX, maxY)
            val best = min(circErr, rectErr)
            if (best > t.closedFitMax) return null // neither fits → keep as ink
            if (corners == 3) return boxShape(ShapeType.TRIANGLE)
            return if (circErr <= rectErr) boxShape(ShapeType.CIRCLE) else boxShape(ShapeType.RECTANGLE)
        }


        // 2) Open shapes → straight line or arrow (must be nearly straight overall)
        val linearity = if (pathLen > 0f) dist(start.x, start.y, end.x, end.y) / pathLen else 0f
        if (linearity > t.linearity && corners <= 2) {
            val type = if (hasArrowHead(pts)) ShapeType.ARROW else ShapeType.LINE
            return ShapeElement(
                shapeType = type, startX = start.x, startY = start.y, endX = end.x, endY = end.y,
                color = stroke.color, strokeWidth = stroke.baseWidth
            )
        }

        return null
    }

    /** Mean deviation of points from the circle inscribed in the bounds, normalized by radius. */
    private fun circleFitError(points: List<StrokePoint>, minX: Float, minY: Float, maxX: Float, maxY: Float): Float {
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val rx = (maxX - minX) / 2f
        val ry = (maxY - minY) / 2f
        val r = (rx + ry) / 2f
        if (r < 4f) return Float.MAX_VALUE
        var sum = 0f
        for (p in points) sum += abs(hypot(p.x - cx, p.y - cy) - r)
        return (sum / points.size) / r
    }

    /** Mean distance of points from the nearest bounding-box edge, normalized by half the short side. */
    private fun rectFitError(points: List<StrokePoint>, minX: Float, minY: Float, maxX: Float, maxY: Float): Float {
        val half = (min(maxX - minX, maxY - minY) / 2f)
        if (half < 4f) return Float.MAX_VALUE
        var sum = 0f
        for (p in points) {
            val d = min(min(p.x - minX, maxX - p.x), min(p.y - minY, maxY - p.y))
            sum += abs(d)
        }
        return (sum / points.size) / half
    }

    /** Count vertices where the path turns sharply (~≥50°) after resampling to reduce noise. */
    private fun countCorners(points: List<StrokePoint>): Int {
        val resampled = resample(points, 24)
        if (resampled.size < 3) return 0
        var corners = 0
        var i = 1
        while (i < resampled.size - 1) {
            val a = resampled[i - 1]
            val b = resampled[i]
            val c = resampled[i + 1]
            val ang = turnAngle(a, b, c)
            if (ang > 0.9f) { // ~51 degrees
                corners++
                i += 2 // skip so one corner isn't double counted
            } else {
                i++
            }
        }
        return corners
    }

    private fun turnAngle(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        val a1 = atan2(b[1] - a[1], b[0] - a[0])
        val a2 = atan2(c[1] - b[1], c[0] - b[0])
        var d = abs(a2 - a1)
        if (d > PI) d = (2 * PI - d).toFloat()
        return d.toFloat()
    }

    /** Resample the polyline into [count] evenly spaced points. */
    private fun resample(points: List<StrokePoint>, count: Int): List<FloatArray> {
        val total = pathLength(points)
        if (total <= 0f) return emptyList()
        val step = total / (count - 1)
        val out = ArrayList<FloatArray>(count)
        out.add(floatArrayOf(points.first().x, points.first().y))
        var d = 0f
        var i = 1
        var prevX = points.first().x
        var prevY = points.first().y
        while (i < points.size && out.size < count) {
            val cx = points[i].x; val cy = points[i].y
            val segLen = dist(prevX, prevY, cx, cy)
            if (segLen <= 0f) { i++; continue }
            if (d + segLen >= step) {
                val ratio = (step - d) / segLen
                val nx = prevX + ratio * (cx - prevX)
                val ny = prevY + ratio * (cy - prevY)
                out.add(floatArrayOf(nx, ny))
                prevX = nx; prevY = ny
                d = 0f
            } else {
                d += segLen
                prevX = cx; prevY = cy
                i++
            }
        }
        if (out.size < count) out.add(floatArrayOf(points.last().x, points.last().y))
        return out
    }

    private fun pathLength(points: List<StrokePoint>): Float {
        var len = 0f
        for (i in 0 until points.size - 1) len += dist(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y)
        return len
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)

    private fun hasArrowHead(points: List<StrokePoint>): Boolean {
        if (points.size < 8) return false
        val end = points.last()
        val p2 = points[points.size - 5]
        val mainAngle = atan2(end.y - points.first().y, end.x - points.first().x)
        val tailAngle = atan2(end.y - p2.y, end.x - p2.x)
        val diff = abs(mainAngle - tailAngle)
        return diff > 0.4f
    }
}
