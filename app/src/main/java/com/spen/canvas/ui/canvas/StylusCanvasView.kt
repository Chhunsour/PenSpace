package com.spen.canvas.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.spen.canvas.geometry.ShapeRecognizer
import com.spen.canvas.model.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Ultra-low latency S Pen Canvas engine.
 * Features:
 * 1. Figma-style focal-point zoom (0.2x..10.0x) & pan in World/Canvas coordinates.
 * 2. Momentary S Pen side button temporary eraser shortcut.
 * 3. Finger gesture vs S Pen stylus input disambiguation & palm rejection.
 */
class StylusCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Viewport Transformation Matrix (World / Canvas Coordinates)
    var zoomScale = 1.0f
        private set
    var panX = 0f
        private set
    var panY = 0f
        private set

    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()

    // Tool & Palette Configs
    var activeTool: ActiveTool = ActiveTool.PEN
    var penConfig: PenConfig = PenConfig()
    var highlighterColor: Long = 0xFFFACC15
    var highlighterWidth: Float = 24f
    var eraserMode: EraserMode = EraserMode.PRECISION
    var selectedShapeType: ShapeType = ShapeType.RECTANGLE
    var backgroundType: BackgroundType = BackgroundType.PLAIN
    var lassoSelection: LassoSelection = LassoSelection()

    // Temporary S Pen Side Button Eraser State
    private var isTemporaryEraserActive = false
    private var hoverCanvasX = -1f
    private var hoverCanvasY = -1f

    // Canvas State Collections
    private val strokeList = mutableListOf<InkStroke>()
    private val shapeList = mutableListOf<ShapeElement>()
    private val textList = mutableListOf<TextElement>()

    // Callbacks to ViewModel
    var onStrokeCompleted: ((InkStroke) -> Unit)? = null
    var onShapeCompleted: ((ShapeElement) -> Unit)? = null
    var onLassoCompleted: ((List<StrokePoint>) -> Unit)? = null
    var onStrokesErased: ((Set<String>) -> Unit)? = null
    var onCanvasTransformChanged: ((scale: Float, panX: Float, panY: Float) -> Unit)? = null

    // Live In-Progress Stroke
    private val currentPoints = mutableListOf<StrokePoint>()
    private var currentPath = Path()

    // Hold-at-End Shape Auto-Straightening Handler
    private val holdHandler = Handler(Looper.getMainLooper())
    private var isHoldTriggered = false
    private val holdRunnable = Runnable {
        if (activeTool == ActiveTool.PEN && !isTemporaryEraserActive && currentPoints.size > 5) {
            val candidateStroke = InkStroke(
                points = ArrayList(currentPoints),
                color = penConfig.color,
                baseWidth = penConfig.baseWidth
            )
            val cleanedShape = ShapeRecognizer.recognizeShape(candidateStroke)
            if (cleanedShape != null) {
                isHoldTriggered = true
                currentPoints.clear()
                currentPath.reset()
                onShapeCompleted?.invoke(cleanedShape)
                invalidate()
            }
        }
    }

    // Paints
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val highlighterPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    private val shapePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
    }

    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val eraserCursorPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFFEF4444.toInt()
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val lassoPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFF3B82F6.toInt()
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
    }

    private val bgPaint = Paint().apply {
        isAntiAlias = true
        color = 0x22FFFFFF
        strokeWidth = 1.5f
    }

    private var activePointerId = INVALID_POINTER_ID

    // FIGMA-STYLE FOCAL POINT PAN & ZOOM DETECTOR
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val oldScale = zoomScale
            val newScale = (zoomScale * scaleFactor).coerceIn(0.2f, 10.0f)
            val scaleRatio = newScale / oldScale

            val focusX = detector.focusX
            val focusY = detector.focusY

            // Zoom anchored around gesture focal point
            panX = focusX - (focusX - panX) * scaleRatio
            panY = focusY - (focusY - panY) * scaleRatio
            zoomScale = newScale

            updateMatrix()
            invalidate()
            onCanvasTransformChanged?.invoke(zoomScale, panX, panY)
            return true
        }
    })

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updateMatrix()
    }

    fun setElements(
        strokes: List<InkStroke>,
        shapes: List<ShapeElement>,
        texts: List<TextElement>,
        bg: BackgroundType,
        lasso: LassoSelection
    ) {
        strokeList.clear()
        strokeList.addAll(strokes)

        shapeList.clear()
        shapeList.addAll(shapes)

        textList.clear()
        textList.addAll(texts)

        backgroundType = bg
        lassoSelection = lasso

        invalidate()
    }

    fun setTransform(scale: Float, pX: Float, pY: Float) {
        zoomScale = scale.coerceIn(0.2f, 10.0f)
        panX = pX
        panY = pY
        updateMatrix()
        invalidate()
    }

    private fun updateMatrix() {
        transformMatrix.reset()
        transformMatrix.postScale(zoomScale, zoomScale)
        transformMatrix.postTranslate(panX, panY)
        transformMatrix.invert(inverseMatrix)
    }

    /**
     * Map Screen Coordinates (px) to Canvas World Coordinates.
     */
    fun screenToWorld(screenX: Float, screenY: Float): Pair<Float, Float> {
        val pts = floatArrayOf(screenX, screenY)
        inverseMatrix.mapPoints(pts)
        return pts[0] to pts[1]
    }

    /**
     * Map Canvas World Coordinates to Screen Coordinates (px).
     */
    fun worldToScreen(worldX: Float, worldY: Float): Pair<Float, Float> {
        val pts = floatArrayOf(worldX, worldY)
        transformMatrix.mapPoints(pts)
        return pts[0] to pts[1]
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Handle S Pen Hovering and Button State Detection
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val pointerIndex = event.actionIndex
            val buttonState = event.buttonState

            val isButtonPressed = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                    (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 ||
                    event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER

            isTemporaryEraserActive = isButtonPressed

            val (wX, wY) = screenToWorld(event.getX(pointerIndex), event.getY(pointerIndex))
            hoverCanvasX = wX
            hoverCanvasY = wY

            invalidate()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val toolType = event.getToolType(pointerIndex)

        // 1. MULTI-TOUCH PAN & ZOOM (2-Finger Figma Navigation)
        if (event.pointerCount >= 2) {
            cancelHoldDetector()
            scaleDetector.onTouchEvent(event)

            val focusX = (event.getX(0) + event.getX(1)) / 2f
            val focusY = (event.getY(0) + event.getY(1)) / 2f

            if (action == MotionEvent.ACTION_MOVE) {
                if (lastFocusX != 0f && lastFocusY != 0f) {
                    panX += focusX - lastFocusX
                    panY += focusY - lastFocusY
                    updateMatrix()
                    onCanvasTransformChanged?.invoke(zoomScale, panX, panY)
                }
                lastFocusX = focusX
                lastFocusY = focusY
                invalidate()
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                lastFocusX = 0f
                lastFocusY = 0f
            }
            return true
        }

        // 2. PALM & FINGER REJECTION:
        // Ignore single finger touches for drawing ink so palm/finger touch won't accidently create ink
        if (toolType == MotionEvent.TOOL_TYPE_FINGER && activeTool != ActiveTool.TEXT) {
            return false
        }

        // 3. S PEN SIDE BUTTON MOMENTARY ERASER DETECTOR
        val buttonState = event.buttonState
        val isButtonPressed = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 ||
                toolType == MotionEvent.TOOL_TYPE_ERASER

        isTemporaryEraserActive = isButtonPressed

        // Convert Screen Touch Coordinates -> World Canvas Coordinates
        val (canvasX, canvasY) = screenToWorld(event.getX(pointerIndex), event.getY(pointerIndex))
        hoverCanvasX = canvasX
        hoverCanvasY = canvasY

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(pointerIndex)
                isHoldTriggered = false
                currentPoints.clear()
                currentPath.reset()

                val pressure = sanitizePressure(event.getPressure(pointerIndex))
                addPoint(canvasX, canvasY, pressure, event.eventTime)

                if (activeTool == ActiveTool.PEN && !isTemporaryEraserActive) {
                    scheduleHoldDetector()
                }

                val effectiveEraser = isTemporaryEraserActive || (activeTool == ActiveTool.ERASER)
                if (effectiveEraser && eraserMode == EraserMode.STROKE) {
                    eraseIntersectedStrokes(canvasX, canvasY)
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val ptrIdx = event.findPointerIndex(activePointerId)
                if (ptrIdx != -1) {
                    cancelHoldDetector()

                    val historySize = event.historySize
                    for (h in 0 until historySize) {
                        val (hWorldX, hWorldY) = screenToWorld(
                            event.getHistoricalX(ptrIdx, h),
                            event.getHistoricalY(ptrIdx, h)
                        )
                        val hp = sanitizePressure(event.getHistoricalPressure(ptrIdx, h))
                        val ht = event.getHistoricalEventTime(h)
                        addPoint(hWorldX, hWorldY, hp, ht)
                    }

                    val cp = sanitizePressure(event.getPressure(ptrIdx))
                    addPoint(canvasX, canvasY, cp, event.eventTime)

                    if (activeTool == ActiveTool.PEN && !isTemporaryEraserActive) {
                        scheduleHoldDetector()
                    }

                    val effectiveEraser = isTemporaryEraserActive || (activeTool == ActiveTool.ERASER)
                    if (effectiveEraser && eraserMode == EraserMode.STROKE) {
                        eraseIntersectedStrokes(canvasX, canvasY)
                    }

                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelHoldDetector()
                if (!isHoldTriggered) {
                    finalizeStroke()
                }
                activePointerId = INVALID_POINTER_ID
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun addPoint(x: Float, y: Float, pressure: Float, time: Long) {
        if (currentPoints.isNotEmpty()) {
            val last = currentPoints.last()
            val distSq = (x - last.x) * (x - last.x) + (y - last.y) * (y - last.y)
            if (distSq < 0.25f) return
        }

        currentPoints.add(StrokePoint(x, y, pressure, time))

        if (currentPoints.size == 1) {
            currentPath.moveTo(x, y)
        } else if (currentPoints.size == 2) {
            currentPath.lineTo(x, y)
        } else {
            val p0 = currentPoints[currentPoints.size - 2]
            val midX = (p0.x + x) / 2f
            val midY = (p0.y + y) / 2f
            currentPath.quadTo(p0.x, p0.y, midX, midY)
        }
    }

    private fun scheduleHoldDetector() {
        holdHandler.removeCallbacks(holdRunnable)
        holdHandler.postDelayed(holdRunnable, 350)
    }

    private fun cancelHoldDetector() {
        holdHandler.removeCallbacks(holdRunnable)
    }

    private fun eraseIntersectedStrokes(x: Float, y: Float) {
        val erasedIds = mutableSetOf<String>()
        val eraseRadius = penConfig.baseWidth * 2.5f
        for (stroke in strokeList) {
            if (stroke.containsPoint(x, y, eraseRadius)) {
                erasedIds.add(stroke.id)
            }
        }
        if (erasedIds.isNotEmpty()) {
            onStrokesErased?.invoke(erasedIds)
        }
    }

    private fun finalizeStroke() {
        if (currentPoints.isEmpty()) return

        val effectiveEraser = isTemporaryEraserActive || (activeTool == ActiveTool.ERASER)

        if (effectiveEraser) {
            if (eraserMode == EraserMode.PRECISION || isTemporaryEraserActive) {
                val stroke = InkStroke(
                    points = ArrayList(currentPoints),
                    color = 0x00000000,
                    baseWidth = penConfig.baseWidth * 2.5f,
                    isEraser = true
                )
                onStrokeCompleted?.invoke(stroke)
            }
        } else {
            when (activeTool) {
                ActiveTool.PEN -> {
                    val stroke = InkStroke(
                        points = ArrayList(currentPoints),
                        color = penConfig.color,
                        alpha = 1.0f,
                        baseWidth = penConfig.baseWidth,
                        isHighlighter = false,
                        isEraser = false
                    )
                    onStrokeCompleted?.invoke(stroke)
                }

                ActiveTool.HIGHLIGHTER -> {
                    val stroke = InkStroke(
                        points = ArrayList(currentPoints),
                        color = highlighterColor,
                        alpha = 0.45f,
                        baseWidth = highlighterWidth,
                        isHighlighter = true,
                        isEraser = false
                    )
                    onStrokeCompleted?.invoke(stroke)
                }

                ActiveTool.LASSO -> {
                    onLassoCompleted?.invoke(ArrayList(currentPoints))
                }

                ActiveTool.SHAPE -> {
                    val first = currentPoints.first()
                    val last = currentPoints.last()
                    val shape = ShapeElement(
                        shapeType = selectedShapeType,
                        startX = first.x,
                        startY = first.y,
                        endX = last.x,
                        endY = last.y,
                        color = penConfig.color,
                        strokeWidth = penConfig.baseWidth
                    )
                    onShapeCompleted?.invoke(shape)
                }

                ActiveTool.ERASER, ActiveTool.TEXT -> {}
            }
        }

        currentPoints.clear()
        currentPath.reset()
    }

    private fun sanitizePressure(p: Float): Float {
        if (p <= 0.001f) return 0.5f
        return p.coerceIn(0.1f, 2.0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Apply World Matrix Transformation
        canvas.save()
        canvas.concat(transformMatrix)

        // 1. Render Infinite Canvas Background Pattern
        drawBackgroundPattern(canvas)

        // 2. Render Vector Shapes
        for (shape in shapeList) {
            renderShape(canvas, shape)
        }

        // 3. Render Ink Strokes & Highlighters
        for (stroke in strokeList) {
            renderStroke(canvas, stroke)
        }

        // 4. Render Typed Text
        for (textElem in textList) {
            textPaint.color = textElem.color.toInt()
            textPaint.textSize = textElem.fontSize
            canvas.drawText(textElem.text, textElem.x, textElem.y, textPaint)
        }

        // 5. Render Live In-Progress Tool Stroke
        if (currentPoints.isNotEmpty()) {
            renderLiveToolStroke(canvas)
        }

        // 6. Render Temporary S Pen Button Eraser Hover Indicator
        if (isTemporaryEraserActive && hoverCanvasX >= 0f && hoverCanvasY >= 0f) {
            val radius = penConfig.baseWidth * 1.5f
            canvas.drawCircle(hoverCanvasX, hoverCanvasY, radius, eraserCursorPaint)
        }

        // 7. Render Lasso Overlay
        if (lassoSelection.isActive()) {
            renderLassoBoundingBox(canvas)
        }

        canvas.restore()
    }

    private fun drawBackgroundPattern(canvas: Canvas) {
        val w = (width.toFloat() / zoomScale) + abs(panX) * 2f
        val h = (height.toFloat() / zoomScale) + abs(panY) * 2f

        when (backgroundType) {
            BackgroundType.PLAIN -> {}

            BackgroundType.DOTS -> {
                val step = 40f
                var x = -w
                while (x < w * 2) {
                    var y = -h
                    while (y < h * 2) {
                        canvas.drawCircle(x, y, 2.5f, bgPaint)
                        y += step
                    }
                    x += step
                }
            }

            BackgroundType.GRID -> {
                val step = 50f
                var x = -w
                while (x < w * 2) {
                    canvas.drawLine(x, -h, x, h * 2, bgPaint)
                    x += step
                }
                var y = -h
                while (y < h * 2) {
                    canvas.drawLine(-w, y, w * 2, y, bgPaint)
                    y += step
                }
            }

            BackgroundType.LINES -> {
                val step = 60f
                var y = -h
                while (y < h * 2) {
                    canvas.drawLine(-w, y, w * 2, y, bgPaint)
                    y += step
                }
            }
        }
    }

    private fun renderStroke(canvas: Canvas, stroke: InkStroke) {
        if (stroke.points.isEmpty()) return

        if (stroke.isEraser) {
            eraserPaint.strokeWidth = stroke.baseWidth
            canvas.drawPath(stroke.createPath(), eraserPaint)
            return
        }

        val paint = if (stroke.isHighlighter) {
            highlighterPaint.apply {
                color = stroke.color.toInt()
                alpha = (stroke.alpha * 255).toInt()
                strokeWidth = stroke.baseWidth
            }
        } else {
            strokePaint.apply {
                color = stroke.color.toInt()
                strokeWidth = stroke.baseWidth
            }
        }

        if (stroke.points.size < 3 || stroke.isHighlighter) {
            canvas.drawPath(stroke.createPath(), paint)
        } else {
            val points = stroke.points
            val baseW = stroke.baseWidth
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val avgP = (p1.pressure + p2.pressure) / 2f
                val dynamicW = (baseW * avgP).coerceIn(baseW * 0.3f, baseW * 2.5f)
                paint.strokeWidth = dynamicW
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }
        }
    }

    private fun renderLiveToolStroke(canvas: Canvas) {
        val effectiveEraser = isTemporaryEraserActive || (activeTool == ActiveTool.ERASER)

        if (effectiveEraser) {
            if (eraserMode == EraserMode.PRECISION || isTemporaryEraserActive) {
                canvas.drawPath(currentPath, eraserPaint)
            }
            return
        }

        when (activeTool) {
            ActiveTool.PEN -> {
                strokePaint.color = penConfig.color.toInt()
                strokePaint.strokeWidth = penConfig.baseWidth
                canvas.drawPath(currentPath, strokePaint)
            }

            ActiveTool.HIGHLIGHTER -> {
                highlighterPaint.color = highlighterColor.toInt()
                highlighterPaint.alpha = 115
                highlighterPaint.strokeWidth = highlighterWidth
                canvas.drawPath(currentPath, highlighterPaint)
            }

            ActiveTool.LASSO -> {
                canvas.drawPath(currentPath, lassoPaint)
            }

            ActiveTool.SHAPE -> {
                val first = currentPoints.first()
                val last = currentPoints.last()
                val liveShape = ShapeElement(
                    shapeType = selectedShapeType,
                    startX = first.x,
                    startY = first.y,
                    endX = last.x,
                    endY = last.y,
                    color = penConfig.color,
                    strokeWidth = penConfig.baseWidth
                )
                renderShape(canvas, liveShape)
            }

            ActiveTool.ERASER, ActiveTool.TEXT -> {}
        }
    }

    private fun renderShape(canvas: Canvas, shape: ShapeElement) {
        shapePaint.color = shape.color.toInt()
        shapePaint.strokeWidth = shape.strokeWidth

        when (shape.shapeType) {
            ShapeType.LINE -> {
                canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, shapePaint)
            }

            ShapeType.ARROW -> {
                canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, shapePaint)
                val angle = atan2(shape.endY - shape.startY, shape.endX - shape.startX)
                val arrowSize = 35f
                val x1 = shape.endX - arrowSize * cos(angle - Math.PI / 6).toFloat()
                val y1 = shape.endY - arrowSize * sin(angle - Math.PI / 6).toFloat()
                val x2 = shape.endX - arrowSize * cos(angle + Math.PI / 6).toFloat()
                val y2 = shape.endY - arrowSize * sin(angle + Math.PI / 6).toFloat()
                canvas.drawLine(shape.endX, shape.endY, x1, y1, shapePaint)
                canvas.drawLine(shape.endX, shape.endY, x2, y2, shapePaint)
            }

            ShapeType.RECTANGLE -> {
                val left = min(shape.startX, shape.endX)
                val top = min(shape.startY, shape.endY)
                val right = max(shape.startX, shape.endX)
                val bottom = max(shape.startY, shape.endY)
                canvas.drawRect(left, top, right, bottom, shapePaint)
            }

            ShapeType.CIRCLE -> {
                val left = min(shape.startX, shape.endX)
                val top = min(shape.startY, shape.endY)
                val right = max(shape.startX, shape.endX)
                val bottom = max(shape.startY, shape.endY)
                val rect = RectF(left, top, right, bottom)
                canvas.drawOval(rect, shapePaint)
            }
        }
    }

    private fun renderLassoBoundingBox(canvas: Canvas) {
        val bbox = lassoSelection.bounds
        if (bbox.isEmpty) return

        val padding = 12f
        val rect = RectF(bbox.left - padding, bbox.top - padding, bbox.right + padding, bbox.bottom + padding)
        canvas.drawRect(rect, lassoPaint)

        val handleRadius = 10f
        shapePaint.color = 0xFF3B82F6.toInt()
        shapePaint.style = Paint.Style.FILL
        canvas.drawCircle(rect.left, rect.top, handleRadius, shapePaint)
        canvas.drawCircle(rect.right, rect.top, handleRadius, shapePaint)
        canvas.drawCircle(rect.left, rect.bottom, handleRadius, shapePaint)
        canvas.drawCircle(rect.right, rect.bottom, handleRadius, shapePaint)
        shapePaint.style = Paint.Style.STROKE
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
    }
}
