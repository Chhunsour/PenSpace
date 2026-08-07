package com.spen.canvas.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spen.canvas.model.*
import com.spen.canvas.ui.canvas.StylusCanvasView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    viewModel: DrawingViewModel,
    modifier: Modifier = Modifier
) {
    val noteTitle by viewModel.noteTitle.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val penConfig by viewModel.penConfig.collectAsState()
    val highlighterColor by viewModel.highlighterColor.collectAsState()
    val highlighterWidth by viewModel.highlighterWidth.collectAsState()
    val recentColors by viewModel.recentColors.collectAsState()
    val eraserMode by viewModel.eraserMode.collectAsState()
    val selectedShapeType by viewModel.selectedShapeType.collectAsState()
    val backgroundType by viewModel.backgroundType.collectAsState()
    val lassoSelection by viewModel.lassoSelection.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val shapes by viewModel.shapes.collectAsState()
    val textElements by viewModel.textElements.collectAsState()
    val zoomScale by viewModel.zoomScale.collectAsState()
    val panOffset by viewModel.panOffset.collectAsState()
    val recognitionResult by viewModel.recognitionResult.collectAsState()
    val isRecognizing by viewModel.isRecognizing.collectAsState()

    var isTitleEditing by remember { mutableStateOf(false) }
    var editedTitleText by remember { mutableStateOf(noteTitle) }
    var activePopover by remember { mutableStateOf<PopoverType?>(null) }
    var isTextAddDialogOpen by remember { mutableStateOf(false) }
    var newTypedText by remember { mutableStateOf("") }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // High Performance S Pen Stylus Canvas Engine
        AndroidView(
            factory = { ctx ->
                StylusCanvasView(ctx).apply {
                    this.activeTool = activeTool
                    this.penConfig = penConfig
                    this.highlighterColor = highlighterColor
                    this.highlighterWidth = highlighterWidth
                    this.eraserMode = eraserMode
                    this.selectedShapeType = selectedShapeType
                    this.backgroundType = backgroundType
                    this.lassoSelection = lassoSelection

                    this.onStrokeCompleted = { stroke -> viewModel.addStroke(stroke) }
                    this.onShapeCompleted = { shape -> viewModel.addShape(shape) }
                    this.onLassoCompleted = { polygon -> viewModel.selectWithLasso(polygon) }
                    this.onStrokesErased = { ids -> viewModel.removeStrokes(ids) }
                    this.onCanvasTransformChanged = { scale, pX, pY ->
                        viewModel.setZoomAndPan(scale, pX, pY)
                    }
                }
            },
            update = { view ->
                view.activeTool = activeTool
                view.penConfig = penConfig
                view.highlighterColor = highlighterColor
                view.highlighterWidth = highlighterWidth
                view.eraserMode = eraserMode
                view.selectedShapeType = selectedShapeType
                view.backgroundType = backgroundType
                view.lassoSelection = lassoSelection

                view.setElements(strokes, shapes, textElements, backgroundType, lassoSelection)
                view.setTransform(zoomScale, panOffset.first, panOffset.second)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 1. MINIMAL TOP HEADER BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { /* Navigation Back */ },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0x33000000),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                if (isTitleEditing) {
                    OutlinedTextField(
                        value = editedTitleText,
                        onValueChange = { editedTitleText = it },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.setNoteTitle(editedTitleText)
                                isTitleEditing = false
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Save Title", tint = Color.Green)
                            }
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                editedTitleText = noteTitle
                                isTitleEditing = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = noteTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Icon(Icons.Default.Edit, contentDescription = "Edit Title", tint = Color(0x99FFFFFF), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x33000000),
                    modifier = Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { isMoreMenuExpanded = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0x33000000),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(
                        expanded = isMoreMenuExpanded,
                        onDismissRequest = { isMoreMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear Canvas", color = Color(0xFFF87171)) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF87171)) },
                            onClick = {
                                viewModel.clearCanvas()
                                isMoreMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 2. CONTEXTUAL LASSO ACTION BAR (When selection active)
        if (lassoSelection.isActive()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xEE1E293B),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Convert to Text (ML Kit)
                    Button(
                        onClick = { viewModel.recognizeSelectedHandwriting() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Convert to Text")
                    }

                    IconButton(onClick = { viewModel.duplicateLassoSelection() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.White)
                    }

                    IconButton(onClick = { viewModel.deleteLassoSelection() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF87171))
                    }
                }
            }
        }

        // 3. CONTEXTUAL TOOL POPOVER PANELS
        AnimatedVisibility(
            visible = activePopover != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xEE1E293B),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            ) {
                when (activePopover) {
                    PopoverType.PEN -> PenPopoverContent(viewModel, penConfig, recentColors)
                    PopoverType.HIGHLIGHTER -> HighlighterPopoverContent(viewModel, highlighterColor, highlighterWidth)
                    PopoverType.ERASER -> EraserPopoverContent(viewModel, eraserMode)
                    PopoverType.SHAPE -> ShapePopoverContent(viewModel, selectedShapeType)
                    PopoverType.BACKGROUND -> BackgroundPopoverContent(viewModel, backgroundType)
                    null -> {}
                }
            }
        }

        // 4. COMPACT FLOATING BOTTOM TOOL DOCK
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xEE1E293B),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pen Tool
                ToolDockIconButton(
                    icon = Icons.Default.Edit,
                    label = "Pen",
                    isSelected = activeTool == ActiveTool.PEN,
                    accentColor = Color(0xFF3B82F6),
                    onClick = {
                        if (activeTool == ActiveTool.PEN) {
                            activePopover = if (activePopover == PopoverType.PEN) null else PopoverType.PEN
                        } else {
                            viewModel.setActiveTool(ActiveTool.PEN)
                            activePopover = PopoverType.PEN
                        }
                    }
                )

                // Highlighter Tool
                ToolDockIconButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlighter",
                    isSelected = activeTool == ActiveTool.HIGHLIGHTER,
                    accentColor = Color(0xFFFACC15),
                    onClick = {
                        if (activeTool == ActiveTool.HIGHLIGHTER) {
                            activePopover = if (activePopover == PopoverType.HIGHLIGHTER) null else PopoverType.HIGHLIGHTER
                        } else {
                            viewModel.setActiveTool(ActiveTool.HIGHLIGHTER)
                            activePopover = PopoverType.HIGHLIGHTER
                        }
                    }
                )

                // Eraser Tool
                ToolDockIconButton(
                    icon = Icons.Default.AutoFixHigh,
                    label = "Eraser",
                    isSelected = activeTool == ActiveTool.ERASER,
                    accentColor = Color(0xFFEF4444),
                    onClick = {
                        if (activeTool == ActiveTool.ERASER) {
                            activePopover = if (activePopover == PopoverType.ERASER) null else PopoverType.ERASER
                        } else {
                            viewModel.setActiveTool(ActiveTool.ERASER)
                            activePopover = PopoverType.ERASER
                        }
                    }
                )

                // Lasso Selection Tool
                ToolDockIconButton(
                    icon = Icons.Default.Gesture,
                    label = "Lasso",
                    isSelected = activeTool == ActiveTool.LASSO,
                    accentColor = Color(0xFFA855F7),
                    onClick = {
                        viewModel.setActiveTool(ActiveTool.LASSO)
                        activePopover = null
                    }
                )

                // Shape Tool
                ToolDockIconButton(
                    icon = Icons.Default.Category,
                    label = "Shape",
                    isSelected = activeTool == ActiveTool.SHAPE,
                    accentColor = Color(0xFF10B981),
                    onClick = {
                        if (activeTool == ActiveTool.SHAPE) {
                            activePopover = if (activePopover == PopoverType.SHAPE) null else PopoverType.SHAPE
                        } else {
                            viewModel.setActiveTool(ActiveTool.SHAPE)
                            activePopover = PopoverType.SHAPE
                        }
                    }
                )

                // Text Tool
                ToolDockIconButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = activeTool == ActiveTool.TEXT,
                    accentColor = Color(0xFF06B6D4),
                    onClick = {
                        viewModel.setActiveTool(ActiveTool.TEXT)
                        activePopover = null
                        isTextAddDialogOpen = true
                    }
                )

                // Background Selector
                IconButton(onClick = {
                    activePopover = if (activePopover == PopoverType.BACKGROUND) null else PopoverType.BACKGROUND
                }) {
                    Icon(Icons.Default.GridOn, contentDescription = "Background", tint = Color.White)
                }

                VerticalDivider(modifier = Modifier.height(24.dp), color = Color(0x22FFFFFF))

                // Undo
                IconButton(onClick = { viewModel.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.White)
                }

                // Redo
                IconButton(onClick = { viewModel.redo() }) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color.White)
                }
            }
        }

        // 5. ML KIT HANDWRITING RECOGNITION DIALOG
        if (recognitionResult != null || isRecognizing) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissRecognitionDialog() },
                title = { Text("Handwriting to Text") },
                text = {
                    if (isRecognizing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Recognizing handwriting...")
                        }
                    } else {
                        var editableText by remember { mutableStateOf(recognitionResult ?: "") }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Recognized Result:")
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { viewModel.dismissRecognitionDialog() }) {
                                    Text("Cancel")
                                }
                                Button(onClick = { viewModel.confirmHandwritingToText(editableText) }) {
                                    Text("Replace")
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // 6. TYPED TEXT ADD DIALOG
        if (isTextAddDialogOpen) {
            AlertDialog(
                onDismissRequest = { isTextAddDialogOpen = false },
                title = { Text("Add Typed Text") },
                text = {
                    OutlinedTextField(
                        value = newTypedText,
                        onValueChange = { newTypedText = it },
                        placeholder = { Text("Type note text...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newTypedText.isNotBlank()) {
                            viewModel.addTextElement(newTypedText, 100f, 200f)
                            newTypedText = ""
                        }
                        isTextAddDialogOpen = false
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isTextAddDialogOpen = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

enum class PopoverType {
    PEN,
    HIGHLIGHTER,
    ERASER,
    SHAPE,
    BACKGROUND
}

@Composable
fun ToolDockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) accentColor else Color.Transparent,
        modifier = Modifier.clip(CircleShape)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun PenPopoverContent(viewModel: DrawingViewModel, penConfig: PenConfig, recentColors: List<Long>) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pen Thickness & Palette", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PenConfig.THICKNESS_PRESETS.forEach { (w, name) ->
                FilterChip(
                    selected = penConfig.baseWidth == w,
                    onClick = { viewModel.setPenWidth(w) },
                    label = { Text("$name (${w.toInt()}pt)") }
                )
            }
        }
        Text("Colors", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recentColors.forEach { colorArgb ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(colorArgb))
                        .border(if (penConfig.color == colorArgb) 3.dp else 1.dp, Color.White, CircleShape)
                        .clickable { viewModel.setColor(colorArgb) }
                )
            }
        }
    }
}

@Composable
fun HighlighterPopoverContent(viewModel: DrawingViewModel, color: Long, width: Float) {
    val highlighterPalette = listOf(0xFFFACC15, 0xFF4ADE80, 0xFF38BDF8, 0xFFF472B6, 0xFFFB923C)
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Highlighter Color & Width", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            highlighterPalette.forEach { colorArgb ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(colorArgb))
                        .border(if (color == colorArgb) 3.dp else 1.dp, Color.White, CircleShape)
                        .clickable { viewModel.setHighlighterColor(colorArgb) }
                )
            }
        }
    }
}

@Composable
fun EraserPopoverContent(viewModel: DrawingViewModel, mode: EraserMode) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Eraser Mode", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == EraserMode.PRECISION,
                onClick = { viewModel.setEraserMode(EraserMode.PRECISION) },
                label = { Text("Precision Eraser") }
            )
            FilterChip(
                selected = mode == EraserMode.STROKE,
                onClick = { viewModel.setEraserMode(EraserMode.STROKE) },
                label = { Text("Object / Stroke Eraser") }
            )
        }
    }
}

@Composable
fun ShapePopoverContent(viewModel: DrawingViewModel, selectedShape: ShapeType) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vector Shape Selector", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeType.entries.forEach { shape ->
                FilterChip(
                    selected = selectedShape == shape,
                    onClick = { viewModel.setSelectedShapeType(shape) },
                    label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}

@Composable
fun BackgroundPopoverContent(viewModel: DrawingViewModel, selectedBg: BackgroundType) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Canvas Background", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundType.entries.forEach { bg ->
                FilterChip(
                    selected = selectedBg == bg,
                    onClick = { viewModel.setBackgroundType(bg) },
                    label = { Text(bg.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}
