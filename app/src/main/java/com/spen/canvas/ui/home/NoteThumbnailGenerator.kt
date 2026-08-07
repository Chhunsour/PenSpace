package com.spen.canvas.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.spen.canvas.model.CanvasDocument
import com.spen.canvas.model.ShapeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

object NoteThumbnailGenerator {
    private val memoryCache = ConcurrentHashMap<String, WeakReference<Bitmap>>()

    suspend fun getThumbnailBitmap(context: Context, doc: CanvasDocument, targetWidth: Int = 280, targetHeight: Int = 220): Bitmap? = getOrGenerateThumbnail(context, doc, targetWidth, targetHeight)

    suspend fun getOrGenerateThumbnail(context: Context, doc: CanvasDocument, targetWidth: Int = 280, targetHeight: Int = 220): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${doc.id}_${doc.lastModified}"
        memoryCache[cacheKey]?.get()?.let { return@withContext it }

        val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val thumbFile = File(thumbDir, "${cacheKey}.jpg")

        if (thumbFile.exists()) {
            try {
                val bmp = BitmapFactory.decodeFile(thumbFile.absolutePath)
                if (bmp != null) {
                    memoryCache[cacheKey] = WeakReference(bmp)
                    return@withContext bmp
                }
            } catch (e: Exception) {
                thumbFile.delete()
            }
        }

        // Generate fresh bitmap thumbnail
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background color
        val bgColor = when (doc.canvasStyle) {
            com.spen.canvas.model.CanvasStyle.CHARCOAL -> 0xFF0F172A.toInt()
            com.spen.canvas.model.CanvasStyle.WHITE -> 0xFFFFFFFF.toInt()
            com.spen.canvas.model.CanvasStyle.PAPER -> 0xFFFDFBF7.toInt()
            com.spen.canvas.model.CanvasStyle.OLED -> 0xFF000000.toInt()
            null -> 0xFF0F172A.toInt()
        }
        canvas.drawColor(bgColor)



        // Calculate content bounds to center the thumbnail
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var hasContent = false

        fun include(bounds: RectF) {
            hasContent = true
            if (bounds.left < minX) minX = bounds.left
            if (bounds.top < minY) minY = bounds.top
            if (bounds.right > maxX) maxX = bounds.right
            if (bounds.bottom > maxY) maxY = bounds.bottom
        }

        doc.strokes.forEach { include(it.bounds) }
        doc.shapes.forEach { include(it.bounds) }
        doc.textElements.forEach { include(it.bounds) }
        doc.images.forEach { include(it.bounds) }

        if (hasContent) {
            val pad = 40f
            val bw = max(maxX - minX, 100f) + pad * 2
            val bh = max(maxY - minY, 100f) + pad * 2
            val scale = min(targetWidth / bw, targetHeight / bh).coerceIn(0.05f, 2.0f)
            val offsetX = (targetWidth - (maxX - minX) * scale) / 2f - minX * scale
            val offsetY = (targetHeight - (maxY - minY) * scale) / 2f - minY * scale

            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)

            // Draw Images
            val imgPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            for (img in doc.images) {
                try {
                    val imgFile = File(img.localPath)
                    if (imgFile.exists()) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val imgBmp = BitmapFactory.decodeFile(imgFile.absolutePath, opts)
                        if (imgBmp != null) {
                            val rect = RectF(img.x, img.y, img.x + img.width, img.y + img.height)
                            canvas.drawBitmap(imgBmp, null, rect, imgPaint)
                        }
                    }
                } catch (_: Exception) {}
            }

            // Draw Shapes
            val shapePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
            for (sh in doc.shapes) {
                shapePaint.color = sh.color.toInt()
                shapePaint.strokeWidth = sh.strokeWidth
                when (sh.shapeType) {
                    ShapeType.LINE -> canvas.drawLine(sh.startX, sh.startY, sh.endX, sh.endY, shapePaint)
                    ShapeType.RECTANGLE -> canvas.drawRect(sh.startX, sh.startY, sh.endX, sh.endY, shapePaint)
                    ShapeType.CIRCLE -> canvas.drawOval(RectF(sh.startX, sh.startY, sh.endX, sh.endY), shapePaint)
                    else -> canvas.drawRect(sh.startX, sh.startY, sh.endX, sh.endY, shapePaint)
                }
            }

            // Draw Strokes
            val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            for (st in doc.strokes) {
                strokePaint.color = st.color.toInt()
                strokePaint.strokeWidth = st.baseWidth
                if (st.isHighlighter) strokePaint.alpha = (st.alpha * 128).toInt() else strokePaint.alpha = 255
                val path = st.createPath()
                canvas.drawPath(path, strokePaint)
            }

            canvas.restore()
        }

        // Cache to disk
        try {
            thumbFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (_: Exception) {}

        memoryCache[cacheKey] = WeakReference(bitmap)
        bitmap
    }
}
