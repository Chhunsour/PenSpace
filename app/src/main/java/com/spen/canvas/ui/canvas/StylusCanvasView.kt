package com.spen.canvas.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File

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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import com.spen.canvas.geometry.ShapeRecognizer
import com.spen.canvas.model.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Low-latency S Pen canvas engine.
 *
 * Writing pipeline:
 *  - Unbuffered stylus dispatch + historical batching + motion prediction keep ink glued to the tip.
 *  - Live and committed ink use the SAME variable-width renderer, so a stroke never "pops" on release.
 *  - Palm rejection: whenever a stylus pointer is present, finger pointers are ignored entirely, so a
 *    resting hand can never hijack the stroke into a pan/zoom.
 *
 * Navigation (only when no stylus is down):
 *  - One finger pans; two fingers pinch-zoom + pan around the focal point.
 *
 * S Pen side button: momentary eraser at the configured size that never mutates the selected tool.
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

    // Appearance
    var canvasColor: Int = 0xFF0F172A.toInt()
        set(value) { if (field != value) { field = value; invalidate() } }
    var patternColor: Int = 0x1FFFFFFF
        set(value) { if (field != value) { field = value; bgPaint.color = value; invalidate() } }

    // Behavior toggles (mirrored from AppSettings)
    var drawWithFinger: Boolean = false
    var pressureSensitivity: Boolean = true
    var sidePenButtonErases: Boolean = true
    var shapeSnapOnHold: Boolean = true
    var hapticsEnabled: Boolean = true
    var tempEraserSize: Float = 46f
    var strokeSmoothing: SmoothingLevel = SmoothingLevel.STANDARD
    var shapeSensitivity: ShapeSensitivity = ShapeSensitivity.MEDIUM

    // Momentary side-button eraser + hover
    private var isTemporaryEraserActive = false
    private var isHovering = false
    private var hoverCanvasX = 0f
    private var hoverCanvasY = 0f

    // Canvas State Collections
    private val strokeList = mutableListOf<InkStroke>()
    private val shapeList = mutableListOf<ShapeElement>()
    private val textList = mutableListOf<TextElement>()
    private val imageList = mutableListOf<ImageElement>()

    // Reference guards so pan/zoom recomposition doesn't re-copy the whole document
    private var lastStrokesRef: List<InkStroke>? = null
    private var lastShapesRef: List<ShapeElement>? = null
    private var lastTextsRef: List<TextElement>? = null
    private var lastImagesRef: List<ImageElement>? = null
    private var lastLassoRef: LassoSelection? = null

    // Image Bitmap Cache
    private val imageBitmapCache = mutableMapOf<String, Bitmap>()
    private val imagePaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    private fun getImageBitmap(path: String): Bitmap? {
        imageBitmapCache[path]?.let { return it }
        try {
            val file = File(path)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) {
                    imageBitmapCache[path] = bmp
                    return bmp
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Callbacks to ViewModel
    var onStrokeCompleted: ((InkStroke) -> Unit)? = null
    var onShapeCompleted: ((ShapeElement) -> Unit)? = null
    var onLassoCompleted: ((List<StrokePoint>) -> Unit)? = null
    var onStrokesErased: ((Set<String>) -> Unit)? = null
    var onElementsErased: ((Set<String>, Set<String>, Set<String>, Set<String>) -> Unit)? = null
    var onSelectionMoved: ((Float, Float) -> Unit)? = null
    var onTextPlaced: ((Float, Float) -> Unit)? = null
    var onCanvasTransformChanged: ((scale: Float, panX: Float, panY: Float) -> Unit)? = null
    var onDrawingStateChanged: ((isDrawing: Boolean) -> Unit)? = null


    // Live In-Progress Stroke
    private val currentPoints = mutableListOf<StrokePoint>()
    private val predictedPoints = mutableListOf<FloatArray>()

    private val motionPredictor: MotionEventPredictor? = try {
        MotionEventPredictor.newInstance(this)
    } catch (e: Throwable) {
        null
    }

    // Gesture state machine
    private enum class Gesture { NONE, DRAW, PAN, MOVE_SELECTION, TEXT_TAP }
    private var gesture = Gesture.NONE
    private var panLastX = 0f
    private var panLastY = 0f
    private var downScreenX = 0f
    private var downScreenY = 0f
    private var moveDownWorldX = 0f
    private var moveDownWorldY = 0f
    private var selectionDragDx = 0f
    private var selectionDragDy = 0f
    // Latched at stroke start so releasing the side button mid-stroke can't turn an erase into ink.
    private var strokeTempErase = false
    private var strokeErasing = false

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
            val cleanedShape = ShapeRecognizer.recognizeShape(candidateStroke, shapeSensitivity)
            if (cleanedShape != null) {
                isHoldTriggered = true
                currentPoints.clear()
                predictedPoints.clear()
                shapeList.add(cleanedShape) // optimistic commit → no flicker
                onShapeCompleted?.invoke(cleanedShape)
                performHaptic()
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
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
    private val eraserCursorFill = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0x22EF4444
    }
    private val eraserCursorStroke = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xF0EF4444.toInt()
        strokeWidth = 2.5f
    }
    private val penCursorPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
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
        color = patternColor
        strokeWidth = 1.5f
    }

    private val segPath = Path()

    private var activePointerId = INVALID_POINTER_ID

    // Two-finger navigation
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var hasFocus = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = zoomScale
            val newScale = (zoomScale * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val scaleRatio = newScale / oldScale
            val focusX = detector.focusX
            val focusY = detector.focusY
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
        images: List<ImageElement>,
        bg: BackgroundType,
        lasso: LassoSelection
    ) {
        val unchanged = strokes === lastStrokesRef && shapes === lastShapesRef &&
                texts === lastTextsRef && images === lastImagesRef && lasso === lastLassoRef && bg == backgroundType
        if (unchanged) return
        lastStrokesRef = strokes; lastShapesRef = shapes; lastTextsRef = texts; lastImagesRef = images; lastLassoRef = lasso
        strokeList.clear(); strokeList.addAll(strokes)
        shapeList.clear(); shapeList.addAll(shapes)
        textList.clear(); textList.addAll(texts)
        imageList.clear(); imageList.addAll(images)
        backgroundType = bg
        lassoSelection = lasso
        invalidate()
    }


    fun setTransform(scale: Float, pX: Float, pY: Float) {
        val s = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (abs(s - zoomScale) < 1e-4f && abs(pX - panX) < 0.5f && abs(pY - panY) < 0.5f) return
        zoomScale = s; panX = pX; panY = pY
        updateMatrix(); invalidate()
    }

    fun resetView() {
        zoomScale = 1f; panX = 0f; panY = 0f
        updateMatrix()
        onCanvasTransformChanged?.invoke(zoomScale, panX, panY)
        invalidate()
    }

    fun zoomBy(factor: Float) {
        val newScale = (zoomScale * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newScale == zoomScale) return
        val ratio = newScale / zoomScale
        val cx = width / 2f; val cy = height / 2f
        panX = cx - (cx - panX) * ratio
        panY = cy - (cy - panY) * ratio
        zoomScale = newScale
        updateMatrix()
        onCanvasTransformChanged?.invoke(zoomScale, panX, panY)
        invalidate()
    }

    fun fitToContent() {
        val bounds = contentBounds() ?: run { resetView(); return }
        if (width == 0 || height == 0) return
        val pad = 120f
        val availW = (width - pad * 2).coerceAtLeast(1f)
        val availH = (height - pad * 2).coerceAtLeast(1f)
        val bw = bounds.width().coerceAtLeast(1f)
        val bh = bounds.height().coerceAtLeast(1f)
        val scale = min(availW / bw, availH / bh).coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoomScale = scale
        panX = width / 2f - bounds.centerX() * scale
        panY = height / 2f - bounds.centerY() * scale
        updateMatrix()
        onCanvasTransformChanged?.invoke(zoomScale, panX, panY)
        invalidate()
    }

    private fun contentBounds(): RectF? {
        var has = false
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        fun include(r: RectF) {
            has = true
            if (r.left < minX) minX = r.left
            if (r.top < minY) minY = r.top
            if (r.right > maxX) maxX = r.right
            if (r.bottom > maxY) maxY = r.bottom
        }
        strokeList.forEach { include(it.bounds) }
        shapeList.forEach { include(it.bounds) }
        textList.forEach { include(it.bounds) }
        return if (has) RectF(minX, minY, maxX, maxY) else null
    }

    private fun updateMatrix() {
        transformMatrix.reset()
        transformMatrix.postScale(zoomScale, zoomScale)
        transformMatrix.postTranslate(panX, panY)
        transformMatrix.invert(inverseMatrix)
    }

    fun screenToWorld(screenX: Float, screenY: Float): Pair<Float, Float> {
        val pts = floatArrayOf(screenX, screenY)
        inverseMatrix.mapPoints(pts)
        return pts[0] to pts[1]
    }

    private fun performHaptic() {
        if (hapticsEnabled) {
            performHapticFeedback(
                HapticFeedbackConstants.CONTEXT_CLICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        }
    }

    private fun eraserRadius(): Float = (tempEraserSize / 2f).coerceAtLeast(6f)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                val i = event.actionIndex
                val buttonState = event.buttonState
                isTemporaryEraserActive = sidePenButtonErases && (
                        (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                        (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 ||
                        event.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER)
                val (wX, wY) = screenToWorld(event.getX(i), event.getY(i))
                hoverCanvasX = wX; hoverCanvasY = wY
                isHovering = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                isHovering = false
                invalidate()
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun selectionContains(worldX: Float, worldY: Float): Boolean {
        if (!lassoSelection.isActive()) return false
        val b = lassoSelection.bounds
        if (b.isEmpty) return false
        val pad = 24f / zoomScale
        return worldX in (b.left - pad)..(b.right + pad) && worldY in (b.top - pad)..(b.bottom + pad)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        motionPredictor?.record(event)

        // Palm rejection: if a stylus pointer exists, it always wins; fingers are ignored.
        var stylusIdx = -1
        for (i in 0 until event.pointerCount) {
            val tt = event.getToolType(i)
            if (tt == MotionEvent.TOOL_TYPE_STYLUS || tt == MotionEvent.TOOL_TYPE_ERASER) { stylusIdx = i; break }
        }
        if (stylusIdx != -1) return handleStylusTouch(event, stylusIdx)

        return handleFingerTouch(event)
    }

    // ---------------- Stylus (writing) ----------------

    private fun handleStylusTouch(event: MotionEvent, sIdx: Int): Boolean {
        val action = event.actionMasked
        val id = event.getPointerId(sIdx)
        val sx = event.getX(sIdx)
        val sy = event.getY(sIdx)
        val (wx, wy) = screenToWorld(sx, sy)
        hoverCanvasX = wx; hoverCanvasY = wy; isHovering = false

        val buttonState = event.buttonState
        isTemporaryEraserActive = sidePenButtonErases && (
                (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 ||
                event.getToolType(sIdx) == MotionEvent.TOOL_TYPE_ERASER)

        val upLike = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
                (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.actionIndex) == id)

        if (activePointerId != id && !upLike) {
            beginStylus(event, sIdx, id, wx, wy)
            return true
        }

        when {
            upLike -> endStylus()
            action == MotionEvent.ACTION_MOVE -> continueStylus(event, id, wx, wy)
        }
        return true
    }

    private fun beginStylus(event: MotionEvent, sIdx: Int, id: Int, wx: Float, wy: Float) {
        requestUnbufferedDispatch(event)
        cancelHoldDetector()
        predictedPoints.clear()
        activePointerId = id
        downScreenX = event.getX(sIdx)
        downScreenY = event.getY(sIdx)
        isHoldTriggered = false

        gesture = when {
            activeTool == ActiveTool.LASSO && selectionContains(wx, wy) -> {
                moveDownWorldX = wx; moveDownWorldY = wy
                selectionDragDx = 0f; selectionDragDy = 0f
                Gesture.MOVE_SELECTION
            }
            activeTool == ActiveTool.TEXT -> Gesture.TEXT_TAP
            else -> Gesture.DRAW
        }

        if (gesture == Gesture.DRAW) {
            strokeTempErase = isTemporaryEraserActive
            strokeErasing = strokeTempErase || activeTool == ActiveTool.ERASER
            currentPoints.clear()
            addPoint(wx, wy, sanitizePressure(event.getPressure(sIdx)), event.eventTime)
            if (activeTool == ActiveTool.PEN && shapeSnapOnHold && !strokeErasing) scheduleHoldDetector()
            applyLiveErase(wx, wy)
            onDrawingStateChanged?.invoke(true)
        }
        invalidate()
    }


    private fun continueStylus(event: MotionEvent, id: Int, wx: Float, wy: Float) {
        when (gesture) {
            Gesture.MOVE_SELECTION -> {
                selectionDragDx = wx - moveDownWorldX
                selectionDragDy = wy - moveDownWorldY
                invalidate()
            }
            Gesture.DRAW -> {
                val ptrIdx = event.findPointerIndex(id)
                if (ptrIdx == -1) return
                cancelHoldDetector()
                for (h in 0 until event.historySize) {
                    val (hx, hy) = screenToWorld(event.getHistoricalX(ptrIdx, h), event.getHistoricalY(ptrIdx, h))
                    addPoint(hx, hy, sanitizePressure(event.getHistoricalPressure(ptrIdx, h)), event.getHistoricalEventTime(h))
                }
                addPoint(wx, wy, sanitizePressure(event.getPressure(ptrIdx)), event.eventTime)
                updatePrediction(id)
                if (activeTool == ActiveTool.PEN && shapeSnapOnHold && !strokeErasing) scheduleHoldDetector()
                applyLiveErase(wx, wy)
                invalidate()
            }
            else -> {}
        }
    }

    private fun endStylus() {
        when (gesture) {
            Gesture.DRAW -> {
                cancelHoldDetector()
                if (!isHoldTriggered) finalizeStroke()
                onDrawingStateChanged?.invoke(false)
            }
            Gesture.MOVE_SELECTION -> {
                if (selectionDragDx != 0f || selectionDragDy != 0f) {
                    translateLocalSelection(selectionDragDx, selectionDragDy)
                    onSelectionMoved?.invoke(selectionDragDx, selectionDragDy)
                }
                selectionDragDx = 0f; selectionDragDy = 0f
            }
            Gesture.TEXT_TAP -> {
                val (tx, ty) = screenToWorld(downScreenX, downScreenY)
                onTextPlaced?.invoke(tx, ty)
            }
            else -> {}
        }
        predictedPoints.clear()
        gesture = Gesture.NONE
        activePointerId = INVALID_POINTER_ID
        invalidate()
    }


    private fun updatePrediction(id: Int) {
        predictedPoints.clear()
        val pe = try { motionPredictor?.predict() } catch (e: Throwable) { null } ?: return
        try {
            val idx = pe.findPointerIndex(id)
            if (idx >= 0) {
                for (h in 0 until pe.historySize) {
                    val (px, py) = screenToWorld(pe.getHistoricalX(idx, h), pe.getHistoricalY(idx, h))
                    predictedPoints.add(floatArrayOf(px, py))
                }
                val (px, py) = screenToWorld(pe.getX(idx), pe.getY(idx))
                predictedPoints.add(floatArrayOf(px, py))
            }
        } finally {
            pe.recycle()
        }
    }

    // ---------------- Fingers (navigation) ----------------

    private fun handleFingerTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked

        if (event.pointerCount >= 2) {
            if (gesture == Gesture.DRAW && currentPoints.isNotEmpty()) {
                currentPoints.clear(); predictedPoints.clear()
            }
            gesture = Gesture.NONE
            cancelHoldDetector()
            scaleDetector.onTouchEvent(event)
            val focusX = (event.getX(0) + event.getX(1)) / 2f
            val focusY = (event.getY(0) + event.getY(1)) / 2f
            when (action) {
                MotionEvent.ACTION_POINTER_DOWN -> { lastFocusX = focusX; lastFocusY = focusY; hasFocus = true }
                MotionEvent.ACTION_MOVE -> {
                    if (hasFocus) {
                        panX += focusX - lastFocusX
                        panY += focusY - lastFocusY
                        updateMatrix()
                        onCanvasTransformChanged?.invoke(zoomScale, panX, panY)
                    }
                    lastFocusX = focusX; lastFocusY = focusY; hasFocus = true
                    invalidate()
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hasFocus = false
            }
            return true
        }

        val pointerIndex = event.actionIndex
        val sx = event.getX(pointerIndex)
        val sy = event.getY(pointerIndex)
        val (wx, wy) = screenToWorld(sx, sy)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(pointerIndex)
                downScreenX = sx; downScreenY = sy
                gesture = when {
                    activeTool == ActiveTool.LASSO && selectionContains(wx, wy) -> {
                        moveDownWorldX = wx; moveDownWorldY = wy
                        selectionDragDx = 0f; selectionDragDy = 0f
                        Gesture.MOVE_SELECTION
                    }
                    activeTool == ActiveTool.TEXT -> Gesture.TEXT_TAP
                    drawWithFinger -> {
                        strokeTempErase = false
                        strokeErasing = activeTool == ActiveTool.ERASER
                        currentPoints.clear()
                        addPoint(wx, wy, 1.0f, event.eventTime)
                        applyLiveErase(wx, wy)
                        Gesture.DRAW
                    }
                    else -> { panLastX = sx; panLastY = sy; Gesture.PAN }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                when (gesture) {
                    Gesture.PAN -> {
                        panX += sx - panLastX; panY += sy - panLastY
                        panLastX = sx; panLastY = sy
                        updateMatrix()
                        onCanvasTransformChanged?.invoke(zoomScale, panX, panY)
                        invalidate()
                    }
                    Gesture.MOVE_SELECTION -> {
                        selectionDragDx = wx - moveDownWorldX
                        selectionDragDy = wy - moveDownWorldY
                        invalidate()
                    }
                    Gesture.DRAW -> {
                        val ptrIdx = event.findPointerIndex(activePointerId)
                        if (ptrIdx != -1) {
                            for (h in 0 until event.historySize) {
                                val (hx, hy) = screenToWorld(event.getHistoricalX(ptrIdx, h), event.getHistoricalY(ptrIdx, h))
                                addPoint(hx, hy, 1.0f, event.getHistoricalEventTime(h))
                            }
                            addPoint(wx, wy, 1.0f, event.eventTime)
                            applyLiveErase(wx, wy)
                            invalidate()
                        }
                    }
                    else -> {}
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (gesture) {
                    Gesture.DRAW -> finalizeStroke()
                    Gesture.MOVE_SELECTION -> {
                        if (selectionDragDx != 0f || selectionDragDy != 0f) {
                            translateLocalSelection(selectionDragDx, selectionDragDy)
                            onSelectionMoved?.invoke(selectionDragDx, selectionDragDy)
                        }
                        selectionDragDx = 0f; selectionDragDy = 0f
                    }
                    Gesture.TEXT_TAP -> {
                        val ddx = sx - downScreenX; val ddy = sy - downScreenY
                        if (ddx * ddx + ddy * ddy < TAP_SLOP_SQ) {
                            val (tx, ty) = screenToWorld(downScreenX, downScreenY)
                            onTextPlaced?.invoke(tx, ty)
                        }
                    }
                    else -> {}
                }
                gesture = Gesture.NONE
                activePointerId = INVALID_POINTER_ID
                invalidate()
                return true
            }
        }
        return true
    }

    private fun addPoint(x: Float, y: Float, pressure: Float, time: Long) {
        if (currentPoints.isNotEmpty()) {
            val last = currentPoints.last()
            val distSq = (x - last.x) * (x - last.x) + (y - last.y) * (y - last.y)
            // Adaptive drop threshold: keep dense samples when zoomed in for tiny handwriting.
            val minDist = 0.4f / zoomScale
            if (distSq < minDist * minDist) return
        }
        currentPoints.add(StrokePoint(x, y, pressure, time))
    }

    private fun scheduleHoldDetector() {
        holdHandler.removeCallbacks(holdRunnable)
        holdHandler.postDelayed(holdRunnable, 320)
    }

    private fun cancelHoldDetector() {
        holdHandler.removeCallbacks(holdRunnable)
    }

    private fun applyLiveErase(x: Float, y: Float) {

        val strokeToolErase = !strokeTempErase && activeTool == ActiveTool.ERASER && eraserMode == EraserMode.STROKE
        if (!strokeTempErase && !strokeToolErase) return
        eraseElementsNear(x, y, eraserRadius())
    }

    private fun eraseElementsNear(x: Float, y: Float, radius: Float) {
        val erasedStrokeIds = mutableSetOf<String>()
        val erasedShapeIds = mutableSetOf<String>()
        val erasedTextIds = mutableSetOf<String>()
        val erasedImageIds = mutableSetOf<String>()

        for (stroke in strokeList) {
            if (!stroke.isEraser && !stroke.isLocked && stroke.containsPoint(x, y, radius)) erasedStrokeIds.add(stroke.id)
        }
        for (shape in shapeList) {
            if (!shape.isLocked && shape.containsPoint(x, y, radius)) erasedShapeIds.add(shape.id)
        }
        for (text in textList) {
            if (!text.isLocked && text.containsPoint(x, y, radius)) erasedTextIds.add(text.id)
        }
        for (img in imageList) {
            if (!img.isLocked && img.containsPoint(x, y, radius)) erasedImageIds.add(img.id)
        }

        if (erasedStrokeIds.isNotEmpty() || erasedShapeIds.isNotEmpty() || erasedTextIds.isNotEmpty() || erasedImageIds.isNotEmpty()) {
            strokeList.removeAll { it.id in erasedStrokeIds }
            shapeList.removeAll { it.id in erasedShapeIds }
            textList.removeAll { it.id in erasedTextIds }
            imageList.removeAll { it.id in erasedImageIds }
            onElementsErased?.invoke(erasedStrokeIds, erasedShapeIds, erasedTextIds, erasedImageIds)
            onStrokesErased?.invoke(erasedStrokeIds)
        }
    }


    private fun finalizeStroke() {
        if (strokeTempErase) { currentPoints.clear(); predictedPoints.clear(); return } // erased live

        if (activeTool == ActiveTool.ERASER) {
            if (eraserMode == EraserMode.PRECISION && currentPoints.isNotEmpty()) {
                val stroke = InkStroke(
                    points = ArrayList(currentPoints),
                    color = 0x00000000,
                    baseWidth = tempEraserSize,
                    isEraser = true
                )
                strokeList.add(stroke)
                onStrokeCompleted?.invoke(stroke)
            }
            currentPoints.clear(); predictedPoints.clear()
            return
        }

        if (currentPoints.isEmpty()) return
        when (activeTool) {
            ActiveTool.PEN -> {
                val stroke = InkStroke(points = ArrayList(currentPoints), color = penConfig.color, alpha = 1.0f, baseWidth = penConfig.baseWidth, isHighlighter = false)
                strokeList.add(stroke); onStrokeCompleted?.invoke(stroke)
            }
            ActiveTool.HIGHLIGHTER -> {
                val stroke = InkStroke(points = ArrayList(currentPoints), color = highlighterColor, alpha = 0.4f, baseWidth = highlighterWidth, isHighlighter = true)
                strokeList.add(stroke); onStrokeCompleted?.invoke(stroke)
            }
            ActiveTool.LASSO -> onLassoCompleted?.invoke(ArrayList(currentPoints))
            ActiveTool.SHAPE -> {
                val first = currentPoints.first(); val last = currentPoints.last()
                val shape = ShapeElement(shapeType = selectedShapeType, startX = first.x, startY = first.y, endX = last.x, endY = last.y, color = penConfig.color, strokeWidth = penConfig.baseWidth)
                shapeList.add(shape); onShapeCompleted?.invoke(shape)
            }
            ActiveTool.ERASER, ActiveTool.TEXT, ActiveTool.IMAGE -> {}
        }
        currentPoints.clear(); predictedPoints.clear()
    }

    private fun sanitizePressure(p: Float): Float {
        if (!pressureSensitivity) return 1.0f
        if (p <= 0.001f) return 0.5f
        return p.coerceIn(0.08f, 2.0f)
    }

    private fun translateLocalSelection(dx: Float, dy: Float) {
        val sel = lassoSelection
        for (i in strokeList.indices) {
            val s = strokeList[i]
            if (s.id in sel.selectedStrokeIds) {
                val pts = s.points.map { it.copy(x = it.x + dx, y = it.y + dy) }
                strokeList[i] = s.copy(points = pts, bounds = InkStroke.computeBounds(pts))
            }
        }
        for (i in shapeList.indices) {
            val sh = shapeList[i]
            if (sh.id in sel.selectedShapeIds) {
                shapeList[i] = sh.copy(
                    startX = sh.startX + dx, startY = sh.startY + dy,
                    endX = sh.endX + dx, endY = sh.endY + dy,
                    bounds = RectF(sh.bounds).apply { offset(dx, dy) }
                )
            }
        }
        for (i in textList.indices) {
            val t = textList[i]
            if (t.id in sel.selectedTextIds) {
                textList[i] = t.copy(x = t.x + dx, y = t.y + dy, bounds = RectF(t.bounds).apply { offset(dx, dy) })
            }
        }
        for (i in imageList.indices) {
            val img = imageList[i]
            if (img.id in sel.selectedImageIds) {
                imageList[i] = img.copy(x = img.x + dx, y = img.y + dy, bounds = RectF(img.bounds).apply { offset(dx, dy) })
            }
        }
    }

    fun exportToBitmap(scale: Float = 2f, margin: Float = 48f): Bitmap? {
        val b = contentBounds() ?: return null
        val outW = (((b.width() + margin * 2) * scale).toInt()).coerceIn(1, 4096)
        val outH = (((b.height() + margin * 2) * scale).toInt()).coerceIn(1, 4096)
        val ink = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val ic = Canvas(ink)
        ic.scale(scale, scale)
        ic.translate(margin - b.left, margin - b.top)
        for (img in imageList) renderImage(ic, img)
        for (shape in shapeList) renderShape(ic, shape)
        for (stroke in strokeList) renderStroke(ic, stroke)
        for (t in textList) {
            textPaint.color = t.color.toInt(); textPaint.textSize = t.fontSize
            ic.drawText(t.text, t.x, t.y, textPaint)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        oc.drawColor(canvasColor)
        oc.drawBitmap(ink, 0f, 0f, null)
        ink.recycle()
        return out
    }

    private fun renderImage(canvas: Canvas, img: ImageElement) {
        val bmp = getImageBitmap(img.localPath) ?: return
        val rect = RectF(img.x, img.y, img.x + img.width, img.y + img.height)
        if (img.rotationDegrees != 0f) {
            canvas.save()
            canvas.rotate(img.rotationDegrees, rect.centerX(), rect.centerY())
            canvas.drawBitmap(bmp, null, rect, imagePaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bmp, null, rect, imagePaint)
        }
    }

    // ---------------- Rendering ----------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(canvasColor)
        canvas.save()
        canvas.concat(transformMatrix)

        drawBackgroundPattern(canvas)

        val movingIds = if (gesture == Gesture.MOVE_SELECTION) lassoSelection else null

        for (img in imageList) {
            val moving = movingIds != null && img.id in movingIds.selectedImageIds
            if (moving) { canvas.save(); canvas.translate(selectionDragDx, selectionDragDy) }
            renderImage(canvas, img)
            if (moving) canvas.restore()
        }
        for (shape in shapeList) {
            val moving = movingIds != null && shape.id in movingIds.selectedShapeIds
            if (moving) { canvas.save(); canvas.translate(selectionDragDx, selectionDragDy) }
            renderShape(canvas, shape)
            if (moving) canvas.restore()
        }
        for (stroke in strokeList) {
            val moving = movingIds != null && stroke.id in movingIds.selectedStrokeIds
            if (moving) { canvas.save(); canvas.translate(selectionDragDx, selectionDragDy) }
            renderStroke(canvas, stroke)
            if (moving) canvas.restore()
        }
        for (textElem in textList) {
            val moving = movingIds != null && textElem.id in movingIds.selectedTextIds
            if (moving) { canvas.save(); canvas.translate(selectionDragDx, selectionDragDy) }
            textPaint.color = textElem.color.toInt(); textPaint.textSize = textElem.fontSize
            canvas.drawText(textElem.text, textElem.x, textElem.y, textPaint)
            if (moving) canvas.restore()
        }


        if (currentPoints.isNotEmpty()) renderLiveToolStroke(canvas)

        drawCursor(canvas)

        if (lassoSelection.isActive()) renderLassoBoundingBox(canvas)

        canvas.restore()
    }

    private fun drawCursor(canvas: Canvas) {
        val erasing = if (gesture == Gesture.DRAW) strokeErasing
            else isTemporaryEraserActive || (activeTool == ActiveTool.ERASER && isHovering)
        if (erasing) {
            val r = eraserRadius()
            canvas.drawCircle(hoverCanvasX, hoverCanvasY, r, eraserCursorFill)
            canvas.drawCircle(hoverCanvasX, hoverCanvasY, r, eraserCursorStroke)
        } else if (isHovering && (activeTool == ActiveTool.PEN || activeTool == ActiveTool.HIGHLIGHTER)) {
            val color = if (activeTool == ActiveTool.PEN) penConfig.color.toInt() else highlighterColor.toInt()
            val r = (if (activeTool == ActiveTool.PEN) penConfig.baseWidth else highlighterWidth) / 2f + 1f
            penCursorPaint.color = color
            penCursorPaint.alpha = 150
            canvas.drawCircle(hoverCanvasX, hoverCanvasY, r.coerceAtLeast(3f), penCursorPaint)
        }
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
                    while (y < h * 2) { canvas.drawCircle(x, y, 2.5f, bgPaint); y += step }
                    x += step
                }
            }
            BackgroundType.GRID -> {
                val step = 50f
                var x = -w
                while (x < w * 2) { canvas.drawLine(x, -h, x, h * 2, bgPaint); x += step }
                var y = -h
                while (y < h * 2) { canvas.drawLine(-w, y, w * 2, y, bgPaint); y += step }
            }
            BackgroundType.LINES -> {
                val step = 60f
                var y = -h
                while (y < h * 2) { canvas.drawLine(-w, y, w * 2, y, bgPaint); y += step }
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
        renderInk(
            canvas = canvas,
            points = stroke.points,
            colorInt = stroke.color.toInt(),
            baseWidth = stroke.baseWidth,
            isHighlighter = stroke.isHighlighter,
            alpha = stroke.alpha,
            lead = null
        )
    }

    private fun renderLiveToolStroke(canvas: Canvas) {
        if (strokeTempErase) return // cursor only; ink erased live
        when (activeTool) {
            ActiveTool.PEN -> renderInk(canvas, currentPoints, penConfig.color.toInt(), penConfig.baseWidth, false, 1f, predictedPoints)
            ActiveTool.HIGHLIGHTER -> renderInk(canvas, currentPoints, highlighterColor.toInt(), highlighterWidth, true, 0.4f, predictedPoints)
            ActiveTool.ERASER -> {
                if (eraserMode == EraserMode.PRECISION) {
                    eraserPaint.strokeWidth = tempEraserSize
                    canvas.drawPath(buildSmoothPath(currentPoints), eraserPaint)
                }
            }
            ActiveTool.LASSO -> canvas.drawPath(buildSmoothPath(currentPoints), lassoPaint)
            ActiveTool.SHAPE -> {
                val first = currentPoints.first(); val last = currentPoints.last()
                renderShape(canvas, ShapeElement(shapeType = selectedShapeType, startX = first.x, startY = first.y, endX = last.x, endY = last.y, color = penConfig.color, strokeWidth = penConfig.baseWidth))
            }
            ActiveTool.TEXT, ActiveTool.IMAGE -> {}
        }
    }


    /**
     * Unified ink renderer used for both live and committed strokes (so there is no "pop" on release).
     * Highlighter and pressure-off strokes draw a single smooth constant-width path; pressure pen ink
     * draws smoothed variable-width quad segments. [lead] renders predicted points as a light tip lead.
     */
    private fun renderInk(
        canvas: Canvas,
        points: List<StrokePoint>,
        colorInt: Int,
        baseWidth: Float,
        isHighlighter: Boolean,
        alpha: Float,
        lead: List<FloatArray>?
    ) {
        if (points.isEmpty()) return
        val src = if (strokeSmoothing == SmoothingLevel.EXTRA) averagePositions(points) else points

        if (isHighlighter || !pressureSensitivity || src.size < 3) {
            val paint = if (isHighlighter) highlighterPaint else strokePaint
            paint.color = colorInt
            paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            paint.strokeWidth = baseWidth
            canvas.drawPath(buildSmoothPath(src), paint)
            drawLead(canvas, src, lead, baseWidth, paint)
            return
        }

        strokePaint.color = colorInt
        strokePaint.alpha = 255
        val n = src.size
        val smooth = strokeSmoothing != SmoothingLevel.OFF

        // First partial segment: p0 -> midpoint(0,1)
        var p0 = src[0]; var p1 = src[1]
        var m = midpoint(p0, p1)
        segPath.reset(); segPath.moveTo(p0.x, p0.y); segPath.lineTo(m[0], m[1])
        strokePaint.strokeWidth = widthAt(src, 0, baseWidth)
        canvas.drawPath(segPath, strokePaint)

        for (i in 1 until n - 1) {
            val a = src[i - 1]; val b = src[i]; val c = src[i + 1]
            val m1 = midpoint(a, b); val m2 = midpoint(b, c)
            segPath.reset(); segPath.moveTo(m1[0], m1[1])
            if (smooth) segPath.quadTo(b.x, b.y, m2[0], m2[1]) else { segPath.lineTo(b.x, b.y); segPath.lineTo(m2[0], m2[1]) }
            strokePaint.strokeWidth = widthAt(src, i, baseWidth)
            canvas.drawPath(segPath, strokePaint)
        }

        // Last partial segment: midpoint(n-2,n-1) -> p(n-1)
        p0 = src[n - 2]; p1 = src[n - 1]
        m = midpoint(p0, p1)
        segPath.reset(); segPath.moveTo(m[0], m[1]); segPath.lineTo(p1.x, p1.y)
        strokePaint.strokeWidth = widthAt(src, n - 1, baseWidth)
        canvas.drawPath(segPath, strokePaint)

        drawLead(canvas, src, lead, widthAt(src, n - 1, baseWidth), strokePaint)
    }

    private fun drawLead(canvas: Canvas, src: List<StrokePoint>, lead: List<FloatArray>?, width: Float, paint: Paint) {
        if (lead.isNullOrEmpty() || src.isEmpty()) return
        paint.strokeWidth = width
        segPath.reset()
        val last = src.last()
        segPath.moveTo(last.x, last.y)
        for (p in lead) segPath.lineTo(p[0], p[1])
        canvas.drawPath(segPath, paint)
    }

    private fun widthAt(points: List<StrokePoint>, i: Int, baseWidth: Float): Float {
        // Smooth pressure with a small window to remove width wobble.
        val lo = max(0, i - 1); val hi = min(points.size - 1, i + 1)
        var sum = 0f; var cnt = 0
        for (k in lo..hi) { sum += points[k].pressure; cnt++ }
        val p = if (cnt > 0) sum / cnt else 1f
        return (baseWidth * p).coerceIn(baseWidth * 0.35f, baseWidth * 2.1f)
    }

    private fun midpoint(a: StrokePoint, b: StrokePoint) = floatArrayOf((a.x + b.x) / 2f, (a.y + b.y) / 2f)

    private fun averagePositions(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 3) return points
        val out = ArrayList<StrokePoint>(points.size)
        out.add(points.first())
        for (i in 1 until points.size - 1) {
            val a = points[i - 1]; val b = points[i]; val c = points[i + 1]
            out.add(b.copy(x = (a.x + b.x * 2f + c.x) / 4f, y = (a.y + b.y * 2f + c.y) / 4f))
        }
        out.add(points.last())
        return out
    }

    private fun buildSmoothPath(points: List<StrokePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.addCircle(points[0].x, points[0].y, 1.5f, Path.Direction.CW)
            return path
        }
        path.moveTo(points[0].x, points[0].y)
        if (strokeSmoothing == SmoothingLevel.OFF) {
            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        } else {
            for (i in 1 until points.size - 1) {
                val p0 = points[i]; val p1 = points[i + 1]
                path.quadTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
            }
            path.lineTo(points.last().x, points.last().y)
        }
        return path
    }

    private fun renderShape(canvas: Canvas, shape: ShapeElement) {
        shapePaint.color = shape.color.toInt()
        shapePaint.strokeWidth = shape.strokeWidth
        when (shape.shapeType) {
            ShapeType.LINE -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, shapePaint)
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
            ShapeType.RECTANGLE -> canvas.drawRect(
                min(shape.startX, shape.endX), min(shape.startY, shape.endY),
                max(shape.startX, shape.endX), max(shape.startY, shape.endY), shapePaint
            )
            ShapeType.CIRCLE -> canvas.drawOval(
                RectF(min(shape.startX, shape.endX), min(shape.startY, shape.endY),
                    max(shape.startX, shape.endX), max(shape.startY, shape.endY)), shapePaint
            )
            ShapeType.TRIANGLE -> {
                val minX = min(shape.startX, shape.endX); val maxX = max(shape.startX, shape.endX)
                val minY = min(shape.startY, shape.endY); val maxY = max(shape.startY, shape.endY)
                val path = Path().apply {
                    moveTo((minX + maxX) / 2f, minY)
                    lineTo(minX, maxY)
                    lineTo(maxX, maxY)
                    close()
                }
                canvas.drawPath(path, shapePaint)
            }
            ShapeType.DIAMOND -> {
                val minX = min(shape.startX, shape.endX); val maxX = max(shape.startX, shape.endX)
                val minY = min(shape.startY, shape.endY); val maxY = max(shape.startY, shape.endY)
                val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
                val path = Path().apply {
                    moveTo(cx, minY)
                    lineTo(maxX, cy)
                    lineTo(cx, maxY)
                    lineTo(minX, cy)
                    close()
                }
                canvas.drawPath(path, shapePaint)
            }
        }
    }


    private fun renderLassoBoundingBox(canvas: Canvas) {
        val bbox = lassoSelection.bounds
        if (bbox.isEmpty) return
        val dx = if (gesture == Gesture.MOVE_SELECTION) selectionDragDx else 0f
        val dy = if (gesture == Gesture.MOVE_SELECTION) selectionDragDy else 0f
        val padding = 12f
        val rect = RectF(bbox.left - padding + dx, bbox.top - padding + dy, bbox.right + padding + dx, bbox.bottom + padding + dy)
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
        private const val MIN_ZOOM = 0.2f
        private const val MAX_ZOOM = 10.0f
        private const val TAP_SLOP_SQ = 24f * 24f
    }
}
