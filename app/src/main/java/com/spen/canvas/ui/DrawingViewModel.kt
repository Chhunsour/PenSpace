package com.spen.canvas.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spen.canvas.ml.HandwritingRecognizer
import com.spen.canvas.model.*
import com.spen.canvas.repository.CanvasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing full S Pen Workspace state, tools, lasso selection, ML Kit recognition, and local persistence.
 */
class DrawingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CanvasRepository(application)
    private val recognizer = HandwritingRecognizer()

    // Title
    private val _noteTitle = MutableStateFlow("Untitled Note")
    val noteTitle: StateFlow<String> = _noteTitle.asStateFlow()

    // Active Tool
    private val _activeTool = MutableStateFlow(ActiveTool.PEN)
    val activeTool: StateFlow<ActiveTool> = _activeTool.asStateFlow()

    // Tool Configs
    private val _penConfig = MutableStateFlow(PenConfig())
    val penConfig: StateFlow<PenConfig> = _penConfig.asStateFlow()

    private val _highlighterColor = MutableStateFlow(0xFFFACC15) // Golden Yellow
    val highlighterColor: StateFlow<Long> = _highlighterColor.asStateFlow()

    private val _highlighterWidth = MutableStateFlow(24f)
    val highlighterWidth: StateFlow<Float> = _highlighterWidth.asStateFlow()

    private val _recentColors = MutableStateFlow(
        listOf(0xFF0F172A, 0xFF1E40AF, 0xFFB91C1C, 0xFF047857, 0xFFFACC15)
    )
    val recentColors: StateFlow<List<Long>> = _recentColors.asStateFlow()

    private val _eraserMode = MutableStateFlow(EraserMode.PRECISION)
    val eraserMode: StateFlow<EraserMode> = _eraserMode.asStateFlow()

    private val _selectedShapeType = MutableStateFlow(ShapeType.RECTANGLE)
    val selectedShapeType: StateFlow<ShapeType> = _selectedShapeType.asStateFlow()

    private val _backgroundType = MutableStateFlow(BackgroundType.PLAIN)
    val backgroundType: StateFlow<BackgroundType> = _backgroundType.asStateFlow()

    // Canvas Objects
    private val _strokes = MutableStateFlow<List<InkStroke>>(emptyList())
    val strokes: StateFlow<List<InkStroke>> = _strokes.asStateFlow()

    private val _shapes = MutableStateFlow<List<ShapeElement>>(emptyList())
    val shapes: StateFlow<List<ShapeElement>> = _shapes.asStateFlow()

    private val _textElements = MutableStateFlow<List<TextElement>>(emptyList())
    val textElements: StateFlow<List<TextElement>> = _textElements.asStateFlow()

    // Selection
    private val _lassoSelection = MutableStateFlow(LassoSelection())
    val lassoSelection: StateFlow<LassoSelection> = _lassoSelection.asStateFlow()

    // Navigation Zoom/Pan
    private val _zoomScale = MutableStateFlow(1.0f)
    val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

    private val _panOffset = MutableStateFlow(0f to 0f)
    val panOffset: StateFlow<Pair<Float, Float>> = _panOffset.asStateFlow()

    // ML Kit Conversion Dialog state
    private val _recognitionResult = MutableStateFlow<String?>(null)
    val recognitionResult: StateFlow<String?> = _recognitionResult.asStateFlow()

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    // Undo / Redo Stacks
    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    private data class CanvasSnapshot(
        val strokes: List<InkStroke>,
        val shapes: List<ShapeElement>,
        val texts: List<TextElement>
    )

    init {
        loadDocument()
    }

    private fun pushUndoSnapshot() {
        undoStack.add(
            CanvasSnapshot(
                strokes = ArrayList(_strokes.value),
                shapes = ArrayList(_shapes.value),
                texts = ArrayList(_textElements.value)
            )
        )
        redoStack.clear()
        triggerAutosave()
    }

    fun setNoteTitle(title: String) {
        _noteTitle.value = title
        triggerAutosave()
    }

    fun setActiveTool(tool: ActiveTool) {
        _activeTool.value = tool
        if (tool != ActiveTool.LASSO) {
            _lassoSelection.value = LassoSelection()
        }
    }

    fun setPenWidth(width: Float) {
        _penConfig.update { it.copy(baseWidth = width) }
    }

    fun setColor(colorArgb: Long) {
        _penConfig.update { it.copy(color = colorArgb) }
        updateRecentColors(colorArgb)
    }

    fun setHighlighterColor(colorArgb: Long) {
        _highlighterColor.value = colorArgb
        updateRecentColors(colorArgb)
    }

    fun setHighlighterWidth(width: Float) {
        _highlighterWidth.value = width
    }

    private fun updateRecentColors(colorArgb: Long) {
        val current = ArrayList(_recentColors.value)
        current.remove(colorArgb)
        current.add(0, colorArgb)
        if (current.size > 5) current.removeAt(current.size - 1)
        _recentColors.value = current
    }

    fun setEraserMode(mode: EraserMode) {
        _eraserMode.value = mode
    }

    fun setSelectedShapeType(shapeType: ShapeType) {
        _selectedShapeType.value = shapeType
        _activeTool.value = ActiveTool.SHAPE
    }

    fun setBackgroundType(bgType: BackgroundType) {
        _backgroundType.value = bgType
        triggerAutosave()
    }

    fun setZoomAndPan(scale: Float, panX: Float, panY: Float) {
        _zoomScale.value = scale.coerceIn(0.5f, 5.0f)
        _panOffset.value = panX to panY
    }

    // Canvas Mutation
    fun addStroke(stroke: InkStroke) {
        pushUndoSnapshot()
        _strokes.update { it + stroke }
    }

    fun removeStrokes(strokeIds: Set<String>) {
        if (strokeIds.isEmpty()) return
        pushUndoSnapshot()
        _strokes.update { list -> list.filterNot { it.id in strokeIds } }
    }

    fun addShape(shape: ShapeElement) {
        pushUndoSnapshot()
        _shapes.update { it + shape }
    }

    fun addTextElement(text: String, x: Float, y: Float) {
        pushUndoSnapshot()
        val textElem = TextElement(text = text, x = x, y = y)
        _textElements.update { it + textElem }
    }

    fun updateTextElement(id: String, newText: String) {
        pushUndoSnapshot()
        _textElements.update { list ->
            list.map { if (it.id == id) it.copy(text = newText) else it }
        }
    }

    // Lasso Selection
    fun selectWithLasso(polygon: List<StrokePoint>) {
        if (polygon.size < 3) {
            _lassoSelection.value = LassoSelection()
            return
        }

        val enclosedStrokeIds = mutableSetOf<String>()
        val enclosedShapeIds = mutableSetOf<String>()
        val enclosedTextIds = mutableSetOf<String>()

        for (s in _strokes.value) {
            if (s.points.any { pt -> LassoSelection.isPointInPolygon(pt.x, pt.y, polygon) }) {
                enclosedStrokeIds.add(s.id)
            }
        }

        for (sh in _shapes.value) {
            if (LassoSelection.isPointInPolygon(sh.startX, sh.startY, polygon) ||
                LassoSelection.isPointInPolygon(sh.endX, sh.endY, polygon)
            ) {
                enclosedShapeIds.add(sh.id)
            }
        }

        for (t in _textElements.value) {
            if (LassoSelection.isPointInPolygon(t.x, t.y, polygon)) {
                enclosedTextIds.add(t.id)
            }
        }

        val selectedStrokes = _strokes.value.filter { it.id in enclosedStrokeIds }
        val selectedShapes = _shapes.value.filter { it.id in enclosedShapeIds }
        val selectedTexts = _textElements.value.filter { it.id in enclosedTextIds }

        val bbox = LassoSelection.computeBoundingBox(selectedStrokes, selectedShapes, selectedTexts)

        _lassoSelection.value = LassoSelection(
            points = polygon,
            selectedStrokeIds = enclosedStrokeIds,
            selectedShapeIds = enclosedShapeIds,
            selectedTextIds = enclosedTextIds,
            bounds = bbox
        )
    }

    fun deleteLassoSelection() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return

        pushUndoSnapshot()
        _strokes.update { list -> list.filterNot { it.id in sel.selectedStrokeIds } }
        _shapes.update { list -> list.filterNot { it.id in sel.selectedShapeIds } }
        _textElements.update { list -> list.filterNot { it.id in sel.selectedTextIds } }

        _lassoSelection.value = LassoSelection()
    }

    fun duplicateLassoSelection() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return

        pushUndoSnapshot()
        val offset = 40f

        val newStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }.map { stroke ->
            stroke.copy(
                id = java.util.UUID.randomUUID().toString(),
                points = stroke.points.map { pt -> pt.copy(x = pt.x + offset, y = pt.y + offset) }
            )
        }

        val newShapes = _shapes.value.filter { it.id in sel.selectedShapeIds }.map { shape ->
            shape.copy(
                id = java.util.UUID.randomUUID().toString(),
                startX = shape.startX + offset,
                startY = shape.startY + offset,
                endX = shape.endX + offset,
                endY = shape.endY + offset
            )
        }

        val newTexts = _textElements.value.filter { it.id in sel.selectedTextIds }.map { txt ->
            txt.copy(
                id = java.util.UUID.randomUUID().toString(),
                x = txt.x + offset,
                y = txt.y + offset
            )
        }

        _strokes.update { it + newStrokes }
        _shapes.update { it + newShapes }
        _textElements.update { it + newTexts }

        _lassoSelection.value = LassoSelection()
    }

    // Handwriting Recognition ML Kit
    fun recognizeSelectedHandwriting() {
        val sel = _lassoSelection.value
        val selectedStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }

        if (selectedStrokes.isEmpty()) return

        viewModelScope.launch {
            _isRecognizing.value = true
            val result = recognizer.recognizeStrokes(selectedStrokes, "en")
            _isRecognizing.value = false

            result.onSuccess { text ->
                _recognitionResult.value = text.ifBlank { "Unrecognized text" }
            }.onFailure { err ->
                _recognitionResult.value = "Error recognizing text: ${err.localizedMessage}"
            }
        }
    }

    fun dismissRecognitionDialog() {
        _recognitionResult.value = null
    }

    fun confirmHandwritingToText(confirmedText: String) {
        val sel = _lassoSelection.value
        val selectedStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }

        if (selectedStrokes.isNotEmpty() && confirmedText.isNotBlank()) {
            pushUndoSnapshot()

            val bbox = LassoSelection.computeBoundingBox(selectedStrokes, emptyList(), emptyList())
            val textElem = TextElement(
                text = confirmedText,
                x = bbox.left.coerceAtLeast(20f),
                y = bbox.centerY()
            )

            // Remove recognized strokes and insert converted text object
            _strokes.update { list -> list.filterNot { it.id in sel.selectedStrokeIds } }
            _textElements.update { it + textElem }

            _lassoSelection.value = LassoSelection()
        }
        _recognitionResult.value = null
    }

    // Undo / Redo
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(
                CanvasSnapshot(
                    strokes = ArrayList(_strokes.value),
                    shapes = ArrayList(_shapes.value),
                    texts = ArrayList(_textElements.value)
                )
            )
            _strokes.value = prev.strokes
            _shapes.value = prev.shapes
            _textElements.value = prev.texts
            _lassoSelection.value = LassoSelection()
            triggerAutosave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(
                CanvasSnapshot(
                    strokes = ArrayList(_strokes.value),
                    shapes = ArrayList(_shapes.value),
                    texts = ArrayList(_textElements.value)
                )
            )
            _strokes.value = next.strokes
            _shapes.value = next.shapes
            _textElements.value = next.texts
            _lassoSelection.value = LassoSelection()
            triggerAutosave()
        }
    }

    fun clearCanvas() {
        if (_strokes.value.isNotEmpty() || _shapes.value.isNotEmpty() || _textElements.value.isNotEmpty()) {
            pushUndoSnapshot()
            _strokes.value = emptyList()
            _shapes.value = emptyList()
            _textElements.value = emptyList()
            _lassoSelection.value = LassoSelection()
        }
    }

    // Local Storage Persistence
    private fun triggerAutosave() {
        viewModelScope.launch {
            val doc = CanvasDocument(
                title = _noteTitle.value,
                strokes = _strokes.value,
                shapes = _shapes.value,
                textElements = _textElements.value,
                backgroundType = _backgroundType.value,
                zoomScale = _zoomScale.value,
                panX = _panOffset.value.first,
                panY = _panOffset.value.second
            )
            repository.saveDocument(doc)
        }
    }

    private fun loadDocument() {
        viewModelScope.launch {
            val loaded = repository.loadDocument()
            loaded?.let { doc ->
                _noteTitle.value = doc.title
                _strokes.value = doc.strokes
                _shapes.value = doc.shapes
                _textElements.value = doc.textElements
                _backgroundType.value = doc.backgroundType
                _zoomScale.value = doc.zoomScale
                _panOffset.value = doc.panX to doc.panY
            }
        }
    }
}
