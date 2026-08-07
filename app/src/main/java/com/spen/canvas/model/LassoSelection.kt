package com.spen.canvas.model

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Clipboard container for copied / cut canvas content.
 */
data class ClipboardData(
    val strokes: List<InkStroke> = emptyList(),
    val shapes: List<ShapeElement> = emptyList(),
    val textElements: List<TextElement> = emptyList(),
    val images: List<ImageElement> = emptyList()
) {
    fun isEmpty(): Boolean = strokes.isEmpty() && shapes.isEmpty() && textElements.isEmpty() && images.isEmpty()
}

/**
 * Manages Lasso selection state and enclosed elements.
 */
data class LassoSelection(
    val points: List<StrokePoint> = emptyList(),
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedShapeIds: Set<String> = emptySet(),
    val selectedTextIds: Set<String> = emptySet(),
    val selectedImageIds: Set<String> = emptySet(),
    val bounds: RectF = RectF(),
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleFactor: Float = 1.0f,
    val rotationDegrees: Float = 0f
) {
    fun isActive(): Boolean = selectedStrokeIds.isNotEmpty() || selectedShapeIds.isNotEmpty() || selectedTextIds.isNotEmpty() || selectedImageIds.isNotEmpty()



    fun createPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        path.close()
        return path
    }

    companion object {
        /**
         * Ray-casting algorithm to test if a point is inside the lasso polygon.
         */
        fun isPointInPolygon(x: Float, y: Float, polygon: List<StrokePoint>): Boolean {
            var intersects = false
            var j = polygon.size - 1
            for (i in polygon.indices) {
                val pi = polygon[i]
                val pj = polygon[j]

                if ((pi.y > y) != (pj.y > y) &&
                    (x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y + 0.0001f) + pi.x)
                ) {
                    intersects = !intersects
                }
                j = i
            }
            return intersects
        }

        fun computeBoundingBox(
            strokes: List<InkStroke>,
            shapes: List<ShapeElement>,
            texts: List<TextElement>,
            images: List<ImageElement> = emptyList()
        ): RectF {
            if (strokes.isEmpty() && shapes.isEmpty() && texts.isEmpty() && images.isEmpty()) return RectF()

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE

            for (s in strokes) {
                if (s.bounds.left < minX) minX = s.bounds.left
                if (s.bounds.top < minY) minY = s.bounds.top
                if (s.bounds.right > maxX) maxX = s.bounds.right
                if (s.bounds.bottom > maxY) maxY = s.bounds.bottom
            }

            for (sh in shapes) {
                if (sh.bounds.left < minX) minX = sh.bounds.left
                if (sh.bounds.top < minY) minY = sh.bounds.top
                if (sh.bounds.right > maxX) maxX = sh.bounds.right
                if (sh.bounds.bottom > maxY) maxY = sh.bounds.bottom
            }

            for (t in texts) {
                if (t.bounds.left < minX) minX = t.bounds.left
                if (t.bounds.top < minY) minY = t.bounds.top
                if (t.bounds.right > maxX) maxX = t.bounds.right
                if (t.bounds.bottom > maxY) maxY = t.bounds.bottom
            }

            for (img in images) {
                if (img.bounds.left < minX) minX = img.bounds.left
                if (img.bounds.top < minY) minY = img.bounds.top
                if (img.bounds.right > maxX) maxX = img.bounds.right
                if (img.bounds.bottom > maxY) maxY = img.bounds.bottom
            }

            return RectF(minX, minY, maxX, maxY)
        }

    }
}
