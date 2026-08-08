package com.spen.canvas.ui

import android.app.Application

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spen.canvas.ml.HandwritingRecognizer
import com.spen.canvas.model.*
import com.spen.canvas.repository.CanvasRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class NoteSortOrder {
    LAST_MODIFIED,
    TITLE,
    CREATED_AT
}


/**
 * ViewModel managing full S Pen Workspace state, multi-note library, tools, lasso selection, ML Kit recognition,
 * appearance settings and debounced local persistence.
 */
class DrawingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CanvasRepository(application)
    private val recognizer = HandwritingRecognizer()

    // Note Library State
    private val _notesList = MutableStateFlow<List<CanvasDocument>>(emptyList())
    val notesList: StateFlow<List<CanvasDocument>> = _notesList.asStateFlow()

    private val _currentNoteId = MutableStateFlow<String?>(null)
    val currentNoteId: StateFlow<String?> = _currentNoteId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterFavoritesOnly = MutableStateFlow(false)
    val filterFavoritesOnly: StateFlow<Boolean> = _filterFavoritesOnly.asStateFlow()

    private val _sortOrder = MutableStateFlow(NoteSortOrder.LAST_MODIFIED)
    val sortOrder: StateFlow<NoteSortOrder> = _sortOrder.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // Appearance & behavior settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Title
    private val _noteTitle = MutableStateFlow("Untitled Note")
    val noteTitle: StateFlow<String> = _noteTitle.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // Active Tool
    private val _activeTool = MutableStateFlow(ActiveTool.PEN)
    val activeTool: StateFlow<ActiveTool> = _activeTool.asStateFlow()

    // Tool Configs
    private val _penConfig = MutableStateFlow(PenConfig(color = 0xFFF8FAFC))
    val penConfig: StateFlow<PenConfig> = _penConfig.asStateFlow()

    private val _highlighterColor = MutableStateFlow(0xFFFACC15) // Golden Yellow
    val highlighterColor: StateFlow<Long> = _highlighterColor.asStateFlow()

    private val _highlighterWidth = MutableStateFlow(24f)
    val highlighterWidth: StateFlow<Float> = _highlighterWidth.asStateFlow()

    private val _recentColors = MutableStateFlow(
        listOf(0xFFF8FAFC, 0xFF3B82F6, 0xFFEF4444, 0xFF22C55E, 0xFFF59E0B)
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

    private val _images = MutableStateFlow<List<ImageElement>>(emptyList())
    val images: StateFlow<List<ImageElement>> = _images.asStateFlow()

    // Selection & Clipboard
    private val _lassoSelection = MutableStateFlow(LassoSelection())
    val lassoSelection: StateFlow<LassoSelection> = _lassoSelection.asStateFlow()

    private val _clipboard = MutableStateFlow(ClipboardData())
    val clipboard: StateFlow<ClipboardData> = _clipboard.asStateFlow()

    // Focus Mode, Command Palette & Adaptive UI Transparency
    private val _isFocusMode = MutableStateFlow(false)
    val isFocusMode: StateFlow<Boolean> = _isFocusMode.asStateFlow()

    private val _isCommandPaletteOpen = MutableStateFlow(false)
    val isCommandPaletteOpen: StateFlow<Boolean> = _isCommandPaletteOpen.asStateFlow()

    private val _isWritingActive = MutableStateFlow(false)
    val isWritingActive: StateFlow<Boolean> = _isWritingActive.asStateFlow()

    private val _showTrashTab = MutableStateFlow(false)
    val showTrashTab: StateFlow<Boolean> = _showTrashTab.asStateFlow()

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
    private val undoStack = ArrayDeque<CanvasSnapshot>()
    private val redoStack = ArrayDeque<CanvasSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var autosaveJob: Job? = null

    private data class CanvasSnapshot(
        val strokes: List<InkStroke>,
        val shapes: List<ShapeElement>,
        val texts: List<TextElement>,
        val images: List<ImageElement>
    )



    init {
        loadSettings()
        loadNotesList()
    }

    // ---- Note Library Operations ----
    fun loadNotesList() {
        viewModelScope.launch {
            val list = repository.getAllNotes()
            _notesList.value = list
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterFavoritesOnly(favoritesOnly: Boolean) {
        _filterFavoritesOnly.value = favoritesOnly
    }

    fun setSortOrder(order: NoteSortOrder) {
        _sortOrder.value = order
    }

    fun setGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
    }

    fun createNewNote(bgType: BackgroundType = BackgroundType.PLAIN): CanvasDocument {
        val dateFormatted = java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.getDefault()).format(java.util.Date())
        val existingCount = _notesList.value.count { it.title == dateFormatted || it.title.startsWith("$dateFormatted (") }
        val autoTitle = if (existingCount == 0) dateFormatted else "$dateFormatted ($existingCount)"

        val newDoc = CanvasDocument(
            id = UUID.randomUUID().toString(),
            title = autoTitle,
            backgroundType = bgType,
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveDocument(newDoc)
            loadNotesList()
            openNote(newDoc.id)
        }
        return newDoc
    }

    fun openNote(noteId: String) {
        viewModelScope.launch {
            val doc = repository.loadDocument(noteId) ?: return@launch
            _currentNoteId.value = doc.id
            _noteTitle.value = doc.title
            _isFavorite.value = doc.isFavorite
            _strokes.value = doc.strokes
            _shapes.value = doc.shapes
            _textElements.value = doc.textElements
            _images.value = doc.images
            _backgroundType.value = doc.backgroundType
            _zoomScale.value = doc.zoomScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
            _panOffset.value = doc.panX to doc.panY
            doc.canvasStyle?.let { style -> updateSettings { it.copy(canvasStyle = style) } }
            _lassoSelection.value = LassoSelection()
            undoStack.clear()
            redoStack.clear()
            refreshUndoRedoFlags()
        }
    }


    fun closeNote() {
        triggerAutosaveImmediate()
        _currentNoteId.value = null
        loadNotesList()
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val doc = repository.loadDocument(noteId) ?: return@launch
            val updated = doc.copy(isDeleted = true, deletedAt = System.currentTimeMillis())
            repository.saveDocument(updated)
            if (_currentNoteId.value == noteId) {
                _currentNoteId.value = null
            }
            loadNotesList()
        }
    }

    fun restoreNoteFromTrash(noteId: String) {
        viewModelScope.launch {
            val doc = repository.loadDocument(noteId) ?: return@launch
            val updated = doc.copy(isDeleted = false, deletedAt = 0L)
            repository.saveDocument(updated)
            loadNotesList()
        }
    }

    fun purgeNotePermanently(noteId: String) {
        viewModelScope.launch {
            repository.deleteDocument(noteId)
            if (_currentNoteId.value == noteId) {
                _currentNoteId.value = null
            }
            loadNotesList()
        }
    }

    fun duplicateNote(noteId: String) {
        viewModelScope.launch {
            repository.duplicateDocument(noteId)
            loadNotesList()
        }
    }

    fun toggleFavoriteNote(noteId: String) {
        viewModelScope.launch {
            val updated = repository.toggleFavorite(noteId)
            if (updated != null && _currentNoteId.value == noteId) {
                _isFavorite.value = updated.isFavorite
            }
            loadNotesList()
        }
    }

    /**
     * Rename a note from the library without navigating into it.
     * Keeps the in-memory title in sync when the renamed note happens to be open.
     */
    fun renameNote(noteId: String, newTitle: String) {
        val cleaned = newTitle.trim().ifBlank { return }
        viewModelScope.launch {
            val doc = repository.loadDocument(noteId) ?: return@launch
            repository.saveDocument(doc.copy(title = cleaned, lastModified = System.currentTimeMillis()))
            if (_currentNoteId.value == noteId) {
                _noteTitle.value = cleaned
            }
            loadNotesList()
        }
    }

    /** Move several notes to the trash in one pass, refreshing the list once at the end. */
    fun trashNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            noteIds.forEach { id ->
                val doc = repository.loadDocument(id) ?: return@forEach
                repository.saveDocument(doc.copy(isDeleted = true, deletedAt = now))
            }
            if (_currentNoteId.value in noteIds) _currentNoteId.value = null
            loadNotesList()
        }
    }

    /** Restore several notes out of the trash. */
    fun restoreNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { id ->
                val doc = repository.loadDocument(id) ?: return@forEach
                repository.saveDocument(doc.copy(isDeleted = false, deletedAt = 0L))
            }
            loadNotesList()
        }
    }

    /** Permanently delete several notes. Irreversible. */
    fun purgeNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { id -> repository.deleteDocument(id) }
            if (_currentNoteId.value in noteIds) _currentNoteId.value = null
            loadNotesList()
        }
    }

    /** Mark several notes favorite (or clear the flag) in one pass. */
    fun setNotesFavorite(noteIds: Set<String>, favorite: Boolean) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { id ->
                val doc = repository.loadDocument(id) ?: return@forEach
                if (doc.isFavorite != favorite) {
                    repository.saveDocument(doc.copy(isFavorite = favorite))
                }
            }
            if (_currentNoteId.value in noteIds) _isFavorite.value = favorite
            loadNotesList()
        }
    }

    /** Duplicate several notes. */
    fun duplicateNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { id -> repository.duplicateDocument(id) }
            loadNotesList()
        }
    }

    /** Permanently delete everything currently in the trash. Irreversible. */
    fun emptyTrash() {
        viewModelScope.launch {
            _notesList.value.filter { it.isDeleted }.forEach { repository.deleteDocument(it.id) }
            loadNotesList()
        }
    }

    fun toggleFocusMode() {
        _isFocusMode.value = !_isFocusMode.value
    }

    fun setFocusMode(enabled: Boolean) {
        _isFocusMode.value = enabled
    }

    fun toggleCommandPalette() {
        _isCommandPaletteOpen.value = !_isCommandPaletteOpen.value
    }

    fun setCommandPaletteOpen(open: Boolean) {
        _isCommandPaletteOpen.value = open
    }

    fun setWritingActive(active: Boolean) {
        _isWritingActive.value = active
    }

    fun setShowTrashTab(show: Boolean) {
        _showTrashTab.value = show
    }

    fun lockSelectedObjects() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return
        pushUndoSnapshot()

        _strokes.update { list ->
            list.map { if (it.id in sel.selectedStrokeIds) it.copy(isLocked = true) else it }
        }
        _shapes.update { list ->
            list.map { if (it.id in sel.selectedShapeIds) it.copy(isLocked = true) else it }
        }
        _textElements.update { list ->
            list.map { if (it.id in sel.selectedTextIds) it.copy(isLocked = true) else it }
        }
        _images.update { list ->
            list.map { if (it.id in sel.selectedImageIds) it.copy(isLocked = true) else it }
        }
        _lassoSelection.value = LassoSelection()
    }

    fun unlockAllObjects() {
        if (_strokes.value.none { it.isLocked } && _shapes.value.none { it.isLocked } && _textElements.value.none { it.isLocked } && _images.value.none { it.isLocked }) return
        pushUndoSnapshot()

        _strokes.update { list -> list.map { it.copy(isLocked = false) } }
        _shapes.update { list -> list.map { it.copy(isLocked = false) } }
        _textElements.update { list -> list.map { it.copy(isLocked = false) } }
        _images.update { list -> list.map { it.copy(isLocked = false) } }
    }


    fun autoSuggestTitleFromText(text: String) {
        if (_noteTitle.value == "Untitled Note" && text.isNotBlank()) {
            val firstLine = text.trim().lines().firstOrNull()?.take(30)?.trim()
            if (!firstLine.isNullOrBlank()) {
                setNoteTitle(firstLine)
            }
        }
    }


    // ---- Undo bookkeeping ----
    private fun pushUndoSnapshot() {
        undoStack.addLast(
            CanvasSnapshot(_strokes.value, _shapes.value, _textElements.value, _images.value)
        )
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        refreshUndoRedoFlags()
        triggerAutosave()
    }


    private fun refreshUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    // ---- Settings ----
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = transform(old)
        _settings.value = new
        if (old.defaultInk != new.defaultInk && _penConfig.value.color == old.defaultInk) {
            _penConfig.update { it.copy(color = new.defaultInk) }
            updateRecentColors(new.defaultInk)
        }
        persistSettings()
        triggerAutosave()
    }

    private fun persistSettings() {
        viewModelScope.launch { repository.saveSettings(_settings.value) }
    }

    fun setNoteTitle(title: String) {
        _noteTitle.value = title
        triggerAutosave()
    }

    fun toggleCurrentFavorite() {
        _currentNoteId.value?.let { toggleFavoriteNote(it) }
    }

    fun setActiveTool(tool: ActiveTool) {
        _activeTool.value = tool
        if (tool != ActiveTool.LASSO) {
            _lassoSelection.value = LassoSelection()
        }
    }

    fun setPenWidth(width: Float) {
        _penConfig.update { it.copy(baseWidth = width.coerceIn(1f, 48f)) }
    }

    fun setColor(colorArgb: Long) {
        _penConfig.update { it.copy(color = colorArgb) }
        updateRecentColors(colorArgb)
    }

    fun setHighlighterColor(colorArgb: Long) {
        _highlighterColor.value = colorArgb
    }

    fun setHighlighterWidth(width: Float) {
        _highlighterWidth.value = width.coerceIn(8f, 60f)
    }

    private fun updateRecentColors(colorArgb: Long) {
        val current = ArrayList(_recentColors.value)
        current.remove(colorArgb)
        current.add(0, colorArgb)
        while (current.size > 6) current.removeAt(current.size - 1)
        _recentColors.value = current
    }

    fun setEraserMode(mode: EraserMode) {
        _eraserMode.value = mode
    }

    fun setSelectedShapeType(shapeType: ShapeType) {
        _selectedShapeType.value = shapeType
        setActiveTool(ActiveTool.SHAPE)
    }

    fun setBackgroundType(bgType: BackgroundType) {
        _backgroundType.value = bgType
        triggerAutosave()
    }

    fun setZoomAndPan(scale: Float, panX: Float, panY: Float) {
        _zoomScale.value = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        _panOffset.value = panX to panY
    }

    // Canvas Mutation & Universal Erase
    fun addStroke(stroke: InkStroke) {
        pushUndoSnapshot()
        _strokes.update { it + stroke }
    }

    fun removeElements(strokeIds: Set<String>, shapeIds: Set<String>, textIds: Set<String>, imageIds: Set<String> = emptySet()) {
        if (strokeIds.isEmpty() && shapeIds.isEmpty() && textIds.isEmpty() && imageIds.isEmpty()) return
        pushUndoSnapshot()
        if (strokeIds.isNotEmpty()) _strokes.update { list -> list.filterNot { it.id in strokeIds } }
        if (shapeIds.isNotEmpty()) _shapes.update { list -> list.filterNot { it.id in shapeIds } }
        if (textIds.isNotEmpty()) _textElements.update { list -> list.filterNot { it.id in textIds } }
        if (imageIds.isNotEmpty()) _images.update { list -> list.filterNot { it.id in imageIds } }
    }

    fun removeStrokes(strokeIds: Set<String>) {
        removeElements(strokeIds, emptySet(), emptySet(), emptySet())
    }

    fun insertImageFromUri(uri: Uri, viewportCenterX: Float = 0f, viewportCenterY: Float = 0f) {
        viewModelScope.launch {
            val result = repository.copyImageToLocalStore(uri) ?: return@launch
            val (localPath, dimensions) = result
            val (origW, origH) = dimensions
            val maxInitialDim = 600f
            val aspect = origW.toFloat() / max(origH.toFloat(), 1f)
            val w = if (aspect >= 1f) maxInitialDim else maxInitialDim * aspect
            val h = if (aspect >= 1f) maxInitialDim / aspect else maxInitialDim

            val img = ImageElement(
                localPath = localPath,
                x = viewportCenterX - w / 2f,
                y = viewportCenterY - h / 2f,
                width = w,
                height = h
            )
            pushUndoSnapshot()
            _images.update { it + img }
        }
    }

    fun insertImageFromBitmap(bitmap: Bitmap, viewportCenterX: Float = 0f, viewportCenterY: Float = 0f) {
        viewModelScope.launch {
            val result = repository.saveBitmapToLocalStore(bitmap) ?: return@launch
            val (localPath, dimensions) = result
            val (origW, origH) = dimensions
            val maxInitialDim = 600f
            val aspect = origW.toFloat() / max(origH.toFloat(), 1f)
            val w = if (aspect >= 1f) maxInitialDim else maxInitialDim * aspect
            val h = if (aspect >= 1f) maxInitialDim / aspect else maxInitialDim

            val img = ImageElement(
                localPath = localPath,
                x = viewportCenterX - w / 2f,
                y = viewportCenterY - h / 2f,
                width = w,
                height = h
            )
            pushUndoSnapshot()
            _images.update { it + img }
        }
    }

    fun pasteFromClipboard(context: Context, viewportCenterX: Float = 0f, viewportCenterY: Float = 0f) {
        val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clipData = clipManager?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val uri = item.uri
            if (uri != null) {
                val mime = context.contentResolver.getType(uri) ?: ""
                if (mime.startsWith("image/") || uri.scheme == "content" || uri.scheme == "file") {
                    insertImageFromUri(uri, viewportCenterX, viewportCenterY)
                    return
                }
            }
            val text = item.text?.toString() ?: item.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) {
                addTextElement(text, viewportCenterX, viewportCenterY)
                return
            }
        }
        if (!_clipboard.value.isEmpty()) {
            pasteClipboard()
        }
    }


    fun addShape(shape: ShapeElement) {
        pushUndoSnapshot()
        _shapes.update { it + shape }
    }

    fun addTextElement(text: String, x: Float, y: Float, fontSize: Float = 22f, color: Long = _settings.value.defaultInk) {
        if (text.isBlank()) return
        pushUndoSnapshot()
        val textElem = TextElement(text = text, x = x, y = y, fontSize = fontSize, color = color)
        _textElements.update { it + textElem }
    }

    fun updateTextElement(id: String, newText: String) {
        pushUndoSnapshot()
        if (newText.isBlank()) {
            _textElements.update { list -> list.filterNot { it.id == id } }
            return
        }
        _textElements.update { list ->
            list.map {
                if (it.id == id) it.copy(
                    text = newText,
                    bounds = RectF(it.x, it.y - it.fontSize, it.x + max(newText.length * it.fontSize * 0.6f, 40f), it.y + it.fontSize * 0.4f)
                ) else it
            }
        }
    }

    // Lasso Selection & Advanced Object Manipulation
    fun selectWithLasso(polygon: List<StrokePoint>) {
        if (polygon.size < 3) {
            _lassoSelection.value = LassoSelection()
            return
        }

        val enclosedStrokeIds = mutableSetOf<String>()
        val enclosedShapeIds = mutableSetOf<String>()
        val enclosedTextIds = mutableSetOf<String>()
        val enclosedImageIds = mutableSetOf<String>()

        for (s in _strokes.value) {
            if (s.points.any { pt -> LassoSelection.isPointInPolygon(pt.x, pt.y, polygon) }) {
                enclosedStrokeIds.add(s.id)
            }
        }

        for (sh in _shapes.value) {
            if (LassoSelection.isPointInPolygon(sh.startX, sh.startY, polygon) ||
                LassoSelection.isPointInPolygon(sh.endX, sh.endY, polygon) ||
                LassoSelection.isPointInPolygon(sh.bounds.centerX(), sh.bounds.centerY(), polygon)
            ) {
                enclosedShapeIds.add(sh.id)
            }
        }

        for (t in _textElements.value) {
            if (LassoSelection.isPointInPolygon(t.x, t.y, polygon)) {
                enclosedTextIds.add(t.id)
            }
        }

        for (img in _images.value) {
            if (LassoSelection.isPointInPolygon(img.x, img.y, polygon) ||
                LassoSelection.isPointInPolygon(img.bounds.centerX(), img.bounds.centerY(), polygon)
            ) {
                enclosedImageIds.add(img.id)
            }
        }

        val selectedStrokes = _strokes.value.filter { it.id in enclosedStrokeIds }
        val selectedShapes = _shapes.value.filter { it.id in enclosedShapeIds }
        val selectedTexts = _textElements.value.filter { it.id in enclosedTextIds }
        val selectedImgs = _images.value.filter { it.id in enclosedImageIds }

        val bbox = LassoSelection.computeBoundingBox(selectedStrokes, selectedShapes, selectedTexts, selectedImgs)

        _lassoSelection.value = LassoSelection(
            points = polygon,
            selectedStrokeIds = enclosedStrokeIds,
            selectedShapeIds = enclosedShapeIds,
            selectedTextIds = enclosedTextIds,
            selectedImageIds = enclosedImageIds,
            bounds = bbox
        )
    }

    fun clearSelection() {
        if (_lassoSelection.value.isActive()) _lassoSelection.value = LassoSelection()
    }

    fun deleteLassoSelection() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return

        pushUndoSnapshot()
        _strokes.update { list -> list.filterNot { it.id in sel.selectedStrokeIds } }
        _shapes.update { list -> list.filterNot { it.id in sel.selectedShapeIds } }
        _textElements.update { list -> list.filterNot { it.id in sel.selectedTextIds } }
        _images.update { list -> list.filterNot { it.id in sel.selectedImageIds } }

        _lassoSelection.value = LassoSelection()
    }

    fun duplicateLassoSelection() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return

        pushUndoSnapshot()
        val offset = 40f

        val newStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }.map { stroke ->
            val pts = stroke.points.map { pt -> pt.copy(x = pt.x + offset, y = pt.y + offset) }
            stroke.copy(id = UUID.randomUUID().toString(), points = pts, bounds = InkStroke.computeBounds(pts))
        }

        val newShapes = _shapes.value.filter { it.id in sel.selectedShapeIds }.map { shape ->
            shape.copy(
                id = UUID.randomUUID().toString(),
                startX = shape.startX + offset, startY = shape.startY + offset,
                endX = shape.endX + offset, endY = shape.endY + offset,
                bounds = ShapeElement.computeBounds(shape.startX + offset, shape.startY + offset, shape.endX + offset, shape.endY + offset, shape.strokeWidth)
            )
        }

        val newTexts = _textElements.value.filter { it.id in sel.selectedTextIds }.map { txt ->
            txt.copy(
                id = UUID.randomUUID().toString(),
                x = txt.x + offset, y = txt.y + offset,
                bounds = RectF(txt.bounds).apply { offset(offset, offset) }
            )
        }

        val newImages = _images.value.filter { it.id in sel.selectedImageIds }.map { img ->
            img.copy(
                id = UUID.randomUUID().toString(),
                x = img.x + offset, y = img.y + offset,
                bounds = RectF(img.bounds).apply { offset(offset, offset) }
            )
        }

        _strokes.update { it + newStrokes }
        _shapes.update { it + newShapes }
        _textElements.update { it + newTexts }
        _images.update { it + newImages }

        _lassoSelection.value = LassoSelection()
    }

    fun moveLassoSelection(dx: Float, dy: Float) {
        val sel = _lassoSelection.value
        if (!sel.isActive() || (dx == 0f && dy == 0f)) return

        pushUndoSnapshot()

        _strokes.update { list ->
            list.map {
                if (it.id in sel.selectedStrokeIds) {
                    val pts = it.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) }
                    it.copy(points = pts, bounds = InkStroke.computeBounds(pts))
                } else it
            }
        }
        _shapes.update { list ->
            list.map {
                if (it.id in sel.selectedShapeIds) {
                    val nsX = it.startX + dx; val nsY = it.startY + dy
                    val neX = it.endX + dx; val neY = it.endY + dy
                    it.copy(
                        startX = nsX, startY = nsY, endX = neX, endY = neY,
                        bounds = ShapeElement.computeBounds(nsX, nsY, neX, neY, it.strokeWidth)
                    )
                } else it
            }
        }
        _textElements.update { list ->
            list.map {
                if (it.id in sel.selectedTextIds) {
                    it.copy(x = it.x + dx, y = it.y + dy, bounds = RectF(it.bounds).apply { offset(dx, dy) })
                } else it
            }
        }
        _images.update { list ->
            list.map {
                if (it.id in sel.selectedImageIds) {
                    it.copy(x = it.x + dx, y = it.y + dy, bounds = RectF(it.bounds).apply { offset(dx, dy) })
                } else it
            }
        }

        _lassoSelection.update { it.copy(bounds = RectF(it.bounds).apply { offset(dx, dy) }) }
    }


    fun rotateLassoSelection(degrees: Float) {
        val sel = _lassoSelection.value
        if (!sel.isActive() || degrees == 0f) return

        pushUndoSnapshot()
        val cx = sel.bounds.centerX()
        val cy = sel.bounds.centerY()
        val rad = Math.toRadians(degrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        fun rotatePoint(px: Float, py: Float): Pair<Float, Float> {
            val dx = px - cx
            val dy = py - cy
            val rx = cx + (dx * cosA - dy * sinA)
            val ry = cy + (dx * sinA + dy * cosA)
            return rx to ry
        }

        _strokes.update { list ->
            list.map { stroke ->
                if (stroke.id in sel.selectedStrokeIds) {
                    val newPts = stroke.points.map { p ->
                        val (rx, ry) = rotatePoint(p.x, p.y)
                        p.copy(x = rx, y = ry)
                    }
                    stroke.copy(points = newPts, bounds = InkStroke.computeBounds(newPts))
                } else stroke
            }
        }

        _shapes.update { list ->
            list.map { shape ->
                if (shape.id in sel.selectedShapeIds) {
                    val (nsX, nsY) = rotatePoint(shape.startX, shape.startY)
                    val (neX, neY) = rotatePoint(shape.endX, shape.endY)
                    shape.copy(
                        startX = nsX, startY = nsY, endX = neX, endY = neY,
                        bounds = ShapeElement.computeBounds(nsX, nsY, neX, neY, shape.strokeWidth)
                    )
                } else shape
            }
        }

        _textElements.update { list ->
            list.map { txt ->
                if (txt.id in sel.selectedTextIds) {
                    val (rx, ry) = rotatePoint(txt.x, txt.y)
                    txt.copy(x = rx, y = ry, bounds = RectF(txt.bounds).apply { offset(rx - txt.x, ry - txt.y) })
                } else txt
            }
        }

        _images.update { list ->
            list.map { img ->
                if (img.id in sel.selectedImageIds) {
                    val (rx, ry) = rotatePoint(img.x, img.y)
                    val newRot = (img.rotationDegrees + degrees) % 360f
                    img.copy(x = rx, y = ry, rotationDegrees = newRot, bounds = RectF(img.bounds).apply { offset(rx - img.x, ry - img.y) })
                } else img
            }
        }

        val recomputedBbox = LassoSelection.computeBoundingBox(
            _strokes.value.filter { it.id in sel.selectedStrokeIds },
            _shapes.value.filter { it.id in sel.selectedShapeIds },
            _textElements.value.filter { it.id in sel.selectedTextIds },
            _images.value.filter { it.id in sel.selectedImageIds }
        )
        _lassoSelection.update { it.copy(bounds = recomputedBbox) }
    }

    fun scaleLassoSelection(factor: Float) {
        val sel = _lassoSelection.value
        if (!sel.isActive() || factor <= 0.1f || factor == 1.0f) return

        pushUndoSnapshot()
        val cx = sel.bounds.centerX()
        val cy = sel.bounds.centerY()

        fun scalePoint(px: Float, py: Float): Pair<Float, Float> {
            val sx = cx + (px - cx) * factor
            val sy = cy + (py - cy) * factor
            return sx to sy
        }

        _strokes.update { list ->
            list.map { stroke ->
                if (stroke.id in sel.selectedStrokeIds) {
                    val newPts = stroke.points.map { p ->
                        val (sx, sy) = scalePoint(p.x, p.y)
                        p.copy(x = sx, y = sy)
                    }
                    stroke.copy(
                        points = newPts,
                        baseWidth = stroke.baseWidth * factor,
                        bounds = InkStroke.computeBounds(newPts)
                    )
                } else stroke
            }
        }

        _shapes.update { list ->
            list.map { shape ->
                if (shape.id in sel.selectedShapeIds) {
                    val (nsX, nsY) = scalePoint(shape.startX, shape.startY)
                    val (neX, neY) = scalePoint(shape.endX, shape.endY)
                    val newWidth = shape.strokeWidth * factor
                    shape.copy(
                        startX = nsX, startY = nsY, endX = neX, endY = neY, strokeWidth = newWidth,
                        bounds = ShapeElement.computeBounds(nsX, nsY, neX, neY, newWidth)
                    )
                } else shape
            }
        }

        _textElements.update { list ->
            list.map { txt ->
                if (txt.id in sel.selectedTextIds) {
                    val (sx, sy) = scalePoint(txt.x, txt.y)
                    val newSize = txt.fontSize * factor
                    txt.copy(x = sx, y = sy, fontSize = newSize, bounds = RectF(sx, sy - newSize, sx + max(txt.text.length * newSize * 0.6f, 40f), sy + newSize * 0.4f))
                } else txt
            }
        }

        _images.update { list ->
            list.map { img ->
                if (img.id in sel.selectedImageIds) {
                    val (sx, sy) = scalePoint(img.x, img.y)
                    val newW = img.width * factor
                    val newH = img.height * factor
                    img.copy(x = sx, y = sy, width = newW, height = newH, bounds = ImageElement.computeBounds(sx, sy, newW, newH))
                } else img
            }
        }

        val recomputedBbox = LassoSelection.computeBoundingBox(
            _strokes.value.filter { it.id in sel.selectedStrokeIds },
            _shapes.value.filter { it.id in sel.selectedShapeIds },
            _textElements.value.filter { it.id in sel.selectedTextIds },
            _images.value.filter { it.id in sel.selectedImageIds }
        )
        _lassoSelection.update { it.copy(bounds = recomputedBbox) }
    }

    fun bringLassoSelectionForward() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return
        pushUndoSnapshot()

        val selectedStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val remainingStrokes = _strokes.value.filterNot { it.id in sel.selectedStrokeIds }
        _strokes.value = remainingStrokes + selectedStrokes

        val selectedShapes = _shapes.value.filter { it.id in sel.selectedShapeIds }
        val remainingShapes = _shapes.value.filterNot { it.id in sel.selectedShapeIds }
        _shapes.value = remainingShapes + selectedShapes

        val selectedTexts = _textElements.value.filter { it.id in sel.selectedTextIds }
        val remainingTexts = _textElements.value.filterNot { it.id in sel.selectedTextIds }
        _textElements.value = remainingTexts + selectedTexts

        val selectedImgs = _images.value.filter { it.id in sel.selectedImageIds }
        val remainingImgs = _images.value.filterNot { it.id in sel.selectedImageIds }
        _images.value = remainingImgs + selectedImgs
    }

    fun sendLassoSelectionBackward() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return
        pushUndoSnapshot()

        val selectedStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val remainingStrokes = _strokes.value.filterNot { it.id in sel.selectedStrokeIds }
        _strokes.value = selectedStrokes + remainingStrokes

        val selectedShapes = _shapes.value.filter { it.id in sel.selectedShapeIds }
        val remainingShapes = _shapes.value.filterNot { it.id in sel.selectedShapeIds }
        _shapes.value = selectedShapes + remainingShapes

        val selectedTexts = _textElements.value.filter { it.id in sel.selectedTextIds }
        val remainingTexts = _textElements.value.filterNot { it.id in sel.selectedTextIds }
        _textElements.value = selectedTexts + remainingTexts

        val selectedImgs = _images.value.filter { it.id in sel.selectedImageIds }
        val remainingImgs = _images.value.filterNot { it.id in sel.selectedImageIds }
        _images.value = selectedImgs + remainingImgs
    }

    fun copyLassoSelection() {
        val sel = _lassoSelection.value
        if (!sel.isActive()) return

        val selStrokes = _strokes.value.filter { it.id in sel.selectedStrokeIds }
        val selShapes = _shapes.value.filter { it.id in sel.selectedShapeIds }
        val selTexts = _textElements.value.filter { it.id in sel.selectedTextIds }
        val selImgs = _images.value.filter { it.id in sel.selectedImageIds }

        _clipboard.value = ClipboardData(selStrokes, selShapes, selTexts, selImgs)
    }

    fun cutLassoSelection() {
        copyLassoSelection()
        deleteLassoSelection()
    }

    fun pasteClipboard() {
        val clip = _clipboard.value
        if (clip.isEmpty()) return

        pushUndoSnapshot()
        val offset = 60f

        val newStrokes = clip.strokes.map { stroke ->
            val pts = stroke.points.map { pt -> pt.copy(x = pt.x + offset, y = pt.y + offset) }
            stroke.copy(id = UUID.randomUUID().toString(), points = pts, bounds = InkStroke.computeBounds(pts))
        }
        val newShapes = clip.shapes.map { shape ->
            val nsX = shape.startX + offset; val nsY = shape.startY + offset
            val neX = shape.endX + offset; val neY = shape.endY + offset
            shape.copy(id = UUID.randomUUID().toString(), startX = nsX, startY = nsY, endX = neX, endY = neY, bounds = ShapeElement.computeBounds(nsX, nsY, neX, neY, shape.strokeWidth))
        }
        val newTexts = clip.textElements.map { txt ->
            txt.copy(id = UUID.randomUUID().toString(), x = txt.x + offset, y = txt.y + offset, bounds = RectF(txt.bounds).apply { offset(offset, offset) })
        }
        val newImages = clip.images.map { img ->
            img.copy(id = UUID.randomUUID().toString(), x = img.x + offset, y = img.y + offset, bounds = RectF(img.bounds).apply { offset(offset, offset) })
        }

        _strokes.update { it + newStrokes }
        _shapes.update { it + newShapes }
        _textElements.update { it + newTexts }
        _images.update { it + newImages }

        val newBbox = LassoSelection.computeBoundingBox(newStrokes, newShapes, newTexts, newImages)
        _lassoSelection.value = LassoSelection(
            selectedStrokeIds = newStrokes.map { it.id }.toSet(),
            selectedShapeIds = newShapes.map { it.id }.toSet(),
            selectedTextIds = newTexts.map { it.id }.toSet(),
            selectedImageIds = newImages.map { it.id }.toSet(),
            bounds = newBbox
        )
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
                y = bbox.centerY(),
                color = _settings.value.defaultInk
            )

            _strokes.update { list -> list.filterNot { it.id in sel.selectedStrokeIds } }
            _textElements.update { it + textElem }

            autoSuggestTitleFromText(confirmedText)
            _lassoSelection.value = LassoSelection()
        }
        _recognitionResult.value = null

    }

    // Undo / Redo
    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(CanvasSnapshot(_strokes.value, _shapes.value, _textElements.value, _images.value))
        _strokes.value = prev.strokes
        _shapes.value = prev.shapes
        _textElements.value = prev.texts
        _images.value = prev.images
        _lassoSelection.value = LassoSelection()
        refreshUndoRedoFlags()
        triggerAutosave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(CanvasSnapshot(_strokes.value, _shapes.value, _textElements.value, _images.value))
        _strokes.value = next.strokes
        _shapes.value = next.shapes
        _textElements.value = next.texts
        _images.value = next.images
        _lassoSelection.value = LassoSelection()
        refreshUndoRedoFlags()
        triggerAutosave()
    }

    fun clearCanvas() {
        if (_strokes.value.isNotEmpty() || _shapes.value.isNotEmpty() || _textElements.value.isNotEmpty() || _images.value.isNotEmpty()) {
            pushUndoSnapshot()
            _strokes.value = emptyList()
            _shapes.value = emptyList()
            _textElements.value = emptyList()
            _images.value = emptyList()
            _lassoSelection.value = LassoSelection()
        }
    }

    val isEmpty: Boolean
        get() = _strokes.value.isEmpty() && _shapes.value.isEmpty() && _textElements.value.isEmpty() && _images.value.isEmpty()

    // Local Storage Persistence
    private fun triggerAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            triggerAutosaveImmediate()
        }
    }

    private fun triggerAutosaveImmediate() {
        val noteId = _currentNoteId.value ?: return
        viewModelScope.launch {
            val doc = CanvasDocument(
                id = noteId,
                title = _noteTitle.value,
                strokes = _strokes.value,
                shapes = _shapes.value,
                textElements = _textElements.value,
                images = _images.value,
                backgroundType = _backgroundType.value,
                canvasStyle = _settings.value.canvasStyle,
                zoomScale = _zoomScale.value,
                panX = _panOffset.value.first,
                panY = _panOffset.value.second,
                isFavorite = _isFavorite.value,
                lastModified = System.currentTimeMillis()
            )
            repository.saveDocument(doc)
            loadNotesList()
        }
    }


    private fun loadSettings() {
        viewModelScope.launch {
            repository.loadSettings()?.let { loaded ->
                _settings.value = loaded
                if (_penConfig.value.color == 0xFFF8FAFC && loaded.isLightCanvas) {
                    _penConfig.update { it.copy(color = loaded.defaultInk) }
                    updateRecentColors(loaded.defaultInk)
                }
            }
        }
    }

    companion object {
        const val MIN_ZOOM = 0.2f
        const val MAX_ZOOM = 10.0f
        private const val MAX_UNDO = 60
        private const val AUTOSAVE_DEBOUNCE_MS = 450L
    }
}

