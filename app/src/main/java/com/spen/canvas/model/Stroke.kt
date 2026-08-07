package com.spen.canvas.model

import android.graphics.Path
import android.graphics.RectF
import java.util.UUID

/**
 * Represents a complete freehand ink stroke consisting of sampled pressure points.
 * Designed to cleanly support future lasso selection, spatial queries, handwriting recognition,
 * undo/redo, and JSON/Room serialization.
 */
data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Long,
    val baseWidth: Float,
    val isEraser: Boolean = false,
    val bounds: RectF = computeBounds(points)
) {
    /**
     * Build smooth graphics Path representation using Quad Bezier curves between point midpoints.
     */
    fun createPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            val pt = points[0]
            path.addCircle(pt.x, pt.y, (baseWidth * pt.pressure).coerceAtLeast(1f) / 2f, Path.Direction.CW)
            return path
        }

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            // Midpoint interpolation for smooth quad curve fit
            val midX = (p0.x + p1.x) / 2f
            val midY = (p0.y + p1.y) / 2f

            path.quadTo(p0.x, p0.y, midX, midY)
        }

        // Final segment to last point
        val lastPoint = points.last()
        path.lineTo(lastPoint.x, lastPoint.y)

        return path
    }

    /**
     * Check if this stroke overlaps with a bounding box (useful for future lasso selection).
     */
    fun intersects(rect: RectF): Boolean {
        return RectF.intersects(bounds, rect)
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
