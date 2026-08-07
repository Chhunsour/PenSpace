package com.spen.canvas.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.spen.canvas.model.*
import com.spen.canvas.ui.canvas.StylusCanvasView
import com.spen.canvas.ui.theme.AppColors
import com.spen.canvas.ui.theme.resolveAppColors
import java.io.File
import java.io.FileOutputStream

private val PEN_PALETTE = listOf(
    0xFFF8FAFC, 0xFF0F172A, 0xFFEF4444, 0xFFF97316,
    0xFFF59E0B, 0xFF22C55E, 0xFF14B8A6, 0xFF3B82F6,
    0xFF6366F1, 0xFFA855F7, 0xFFEC4899, 0xFF78716C
)
private val HIGHLIGHTER_PALETTE = listOf(0xFFFACC15, 0xFF4ADE80, 0xFF38BDF8, 0xFFF472B6, 0xFFFB923C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    viewModel: DrawingViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
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
    val images by viewModel.images.collectAsState()
    val zoomScale by viewModel.zoomScale.collectAsState()
    val panOffset by viewModel.panOffset.collectAsState()
    val recognitionResult by viewModel.recognitionResult.collectAsState()
    val isRecognizing by viewModel.isRecognizing.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val colors = resolveAppColors(settings, systemDark)
    val canvasColor = Color(settings.canvasColor)
    val isWritingActive by viewModel.isWritingActive.collectAsState()
    val isFocusMode by viewModel.isFocusMode.collectAsState()
    val isCommandPaletteOpen by viewModel.isCommandPaletteOpen.collectAsState()

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.insertImageFromUri(uri)
        }
    }

    val animatedOpacity by animateFloatAsState(
        targetValue = if (isWritingActive) 0.15f else settings.toolbarOpacity,
        animationSpec = tween(durationMillis = 200),
        label = "ToolbarOpacity"
    )

    // Translucent "glass" surface for floating controls so the canvas dominates.
    val glass = colors.panel.copy(alpha = animatedOpacity)
    val glassBorder = colors.panelBorder.copy(alpha = (colors.panelBorder.alpha * 1.4f).coerceAtMost(1f))


    val hostView = LocalView.current
    val context = LocalContext.current
    fun tick() {
        if (settings.hapticFeedback) hostView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    var canvasViewRef by remember { mutableStateOf<StylusCanvasView?>(null) }
    var isRenameDialogOpen by remember { mutableStateOf(false) }
    var editedTitleText by remember { mutableStateOf(noteTitle) }

    var activePopover by remember { mutableStateOf<PopoverType?>(null) }
    var isTextDialogOpen by remember { mutableStateOf(false) }
    var pendingTextPos by remember { mutableStateOf(0f to 0f) }
    var newTypedText by remember { mutableStateOf("") }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }
    var isZoomMenuExpanded by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val isCanvasEmpty = strokes.isEmpty() && shapes.isEmpty() && textElements.isEmpty() && images.isEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasColor)
    ) {
        // High Performance S Pen Stylus Canvas Engine
        AndroidView(
            factory = { ctx ->
                StylusCanvasView(ctx).apply {
                    onStrokeCompleted = { viewModel.addStroke(it) }
                    onShapeCompleted = { viewModel.addShape(it) }
                    onLassoCompleted = { viewModel.selectWithLasso(it) }
                    onStrokesErased = { viewModel.removeStrokes(it) }
                    onElementsErased = { sIds, shIds, tIds, imgIds -> viewModel.removeElements(sIds, shIds, tIds, imgIds) }
                    onSelectionMoved = { dx, dy -> viewModel.moveLassoSelection(dx, dy) }
                    onTextPlaced = { x, y ->
                        pendingTextPos = x to y
                        newTypedText = ""
                        isTextDialogOpen = true
                    }
                    onCanvasTransformChanged = { scale, pX, pY -> viewModel.setZoomAndPan(scale, pX, pY) }
                    onDrawingStateChanged = { isWriting -> viewModel.setWritingActive(isWriting) }
                    canvasViewRef = this
                }
            },

            update = { view ->
                view.activeTool = activeTool
                view.penConfig = penConfig
                view.highlighterColor = highlighterColor
                view.highlighterWidth = highlighterWidth
                view.eraserMode = eraserMode
                view.selectedShapeType = selectedShapeType
                view.drawWithFinger = settings.drawWithFinger
                view.pressureSensitivity = settings.pressureSensitivity
                view.sidePenButtonErases = settings.sidePenButtonErases
                view.shapeSnapOnHold = settings.shapeSnapOnHold
                view.hapticsEnabled = settings.hapticFeedback
                view.tempEraserSize = settings.tempEraserSize
                view.strokeSmoothing = settings.strokeSmoothing
                view.shapeSensitivity = settings.shapeSensitivity
                view.canvasColor = settings.canvasColor.toInt()
                view.patternColor = settings.patternColor.toInt()

                view.setElements(strokes, shapes, textElements, images, backgroundType, lassoSelection)
                view.setTransform(zoomScale, panOffset.first, panOffset.second)
            },
            modifier = Modifier.fillMaxSize()
        )


        // Empty-state hint
        if (isCanvasEmpty && activePopover == null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Draw,
                    contentDescription = null,
                    tint = colors.onPanelMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "Start writing with your S Pen",
                    color = colors.onPanelMuted.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "One finger pans • Two fingers zoom • Hold a shape to snap it",
                    color = colors.onPanelMuted.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        // 1. TOP HEADER BAR (Hidden in Focus Mode)
        if (!isFocusMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back to Notes pill & Note Title pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = glass,
                        modifier = Modifier
                            .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                            .clickable { tick(); viewModel.closeNote() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Notes", tint = colors.onPanel, modifier = Modifier.size(18.dp))
                            Text("Notes", style = MaterialTheme.typography.labelLarge, color = colors.onPanel, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = glass,
                        modifier = Modifier
                            .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                tick()
                                editedTitleText = noteTitle
                                isRenameDialogOpen = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = noteTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onPanel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = colors.onPanelMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Right: Zoom pill, Command Palette, Focus Mode, Settings, More
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Zoom pill → view controls menu
                    Box {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = glass,
                            modifier = Modifier
                                .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                                .clickable { isZoomMenuExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom", tint = colors.onPanelMuted, modifier = Modifier.size(15.dp))
                                Text("${(zoomScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onPanel)
                            }
                        }
                        DropdownMenu(
                            expanded = isZoomMenuExpanded,
                            onDismissRequest = { isZoomMenuExpanded = false },
                            modifier = Modifier.background(colors.panel)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Fit to content", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.FitScreen, null, tint = colors.onPanel) },
                                onClick = { canvasViewRef?.fitToContent(); isZoomMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset to 100%", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.CenterFocusStrong, null, tint = colors.onPanel) },
                                onClick = { canvasViewRef?.resetView(); isZoomMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Zoom in (+25%)", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.Add, null, tint = colors.onPanel) },
                                onClick = { canvasViewRef?.zoomBy(1.25f); isZoomMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Zoom out (-25%)", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.Remove, null, tint = colors.onPanel) },
                                onClick = { canvasViewRef?.zoomBy(0.8f); isZoomMenuExpanded = false }
                            )
                        }
                    }

                    // Quick Command Palette button
                    IconButton(
                        onClick = { tick(); viewModel.toggleCommandPalette() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = glass,
                            contentColor = colors.onPanel
                        )
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Command Palette")
                    }

                    // Focus Mode button
                    IconButton(
                        onClick = { tick(); viewModel.toggleFocusMode() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = glass,
                            contentColor = colors.onPanel
                        )
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Focus Mode")
                    }

                    // Direct 1-tap Settings button
                    IconButton(
                        onClick = { tick(); isSettingsOpen = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = glass,
                            contentColor = colors.onPanel
                        )
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }

                    Box {
                        IconButton(
                            onClick = { tick(); isMoreMenuExpanded = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = glass,
                                contentColor = colors.onPanel
                            )
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = isMoreMenuExpanded,
                            onDismissRequest = { isMoreMenuExpanded = false },
                            modifier = Modifier.background(colors.panel)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings & appearance", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.Settings, null, tint = colors.onPanel) },
                                onClick = { isSettingsOpen = true; isMoreMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as image", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.Share, null, tint = colors.onPanel) },
                                onClick = { exportAndShare(context, canvasViewRef); isMoreMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear canvas", color = colors.danger) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = colors.danger) },
                                onClick = { viewModel.clearCanvas(); isMoreMenuExpanded = false }
                            )
                        }
                    }
                }
            }
        }
 else {
            // Floating exit button in Focus Mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = { viewModel.setFocusMode(false) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = glass,
                        contentColor = colors.onPanel
                    )
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Focus Mode")
                }
            }
        }

        // 2. UPGRADED CONTEXTUAL LASSO ACTION BAR

        if (lassoSelection.isActive()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = glass,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp)
                    .border(1.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.recognizeSelectedHandwriting() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("To text", style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(onClick = { viewModel.rotateLassoSelection(-45f) }) {
                        Icon(Icons.Default.RotateLeft, contentDescription = "Rotate -45°", tint = colors.onPanel)
                    }
                    IconButton(onClick = { viewModel.rotateLassoSelection(45f) }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate +45°", tint = colors.onPanel)
                    }
                    IconButton(onClick = { viewModel.scaleLassoSelection(1.2f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Scale Up", tint = colors.onPanel)
                    }
                    IconButton(onClick = { viewModel.scaleLassoSelection(0.8f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Scale Down", tint = colors.onPanel)
                    }

                    IconButton(onClick = { viewModel.copyLassoSelection() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = colors.onPanel)
                    }
                    IconButton(onClick = { viewModel.cutLassoSelection() }) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Cut", tint = colors.onPanel)
                    }
                    IconButton(onClick = { viewModel.duplicateLassoSelection() }) {
                        Icon(Icons.Default.Difference, contentDescription = "Duplicate", tint = colors.onPanel)
                    }
                    IconButton(onClick = { viewModel.bringLassoSelectionForward() }) {
                        Icon(Icons.Default.FlipToFront, contentDescription = "Bring Forward", tint = colors.onPanel)
                    }
                    IconButton(onClick = { viewModel.sendLassoSelectionBackward() }) {
                        Icon(Icons.Default.FlipToBack, contentDescription = "Send Backward", tint = colors.onPanel)
                    }

                    IconButton(onClick = { viewModel.lockSelectedObjects() }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Objects", tint = colors.onPanel)
                    }

                    IconButton(onClick = { viewModel.deleteLassoSelection() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.danger)
                    }
                }
            }
        }

        // 3. CONTEXTUAL TOOL POPOVERS
        if (!isFocusMode) {
            AnimatedVisibility(
                visible = activePopover != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 90.dp, start = 12.dp, end = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = glass,
                    shadowElevation = 6.dp,
                    modifier = Modifier.border(1.dp, glassBorder, RoundedCornerShape(24.dp))
                ) {
                    when (activePopover) {
                        PopoverType.PEN -> PenPopoverContent(viewModel, penConfig, recentColors, colors)
                        PopoverType.HIGHLIGHTER -> HighlighterPopoverContent(viewModel, highlighterColor, highlighterWidth, colors)
                        PopoverType.ERASER -> EraserPopoverContent(viewModel, eraserMode, settings.tempEraserSize, colors)
                        PopoverType.INSERT -> InsertPopoverContent(viewModel, selectedShapeType, activeTool, colors) { activePopover = null }
                        null -> {}
                    }
                }
            }

            // 4. FLOATING BOTTOM TOOL DOCK
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = glass,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()

                .border(1.dp, glassBorder, RoundedCornerShape(28.dp))
        ) {
            val tools: @Composable () -> Unit = {
                ToolDockIconButton(Icons.Default.Edit, "Pen", activeTool == ActiveTool.PEN, Color(0xFF3B82F6), colors) {
                    tick()
                    if (activeTool == ActiveTool.PEN) {
                        activePopover = if (activePopover == PopoverType.PEN) null else PopoverType.PEN
                    } else { viewModel.setActiveTool(ActiveTool.PEN); activePopover = PopoverType.PEN }
                }
                ToolDockIconButton(Icons.Default.Brush, "Highlighter", activeTool == ActiveTool.HIGHLIGHTER, Color(0xFFFACC15), colors) {
                    tick()
                    if (activeTool == ActiveTool.HIGHLIGHTER) {
                        activePopover = if (activePopover == PopoverType.HIGHLIGHTER) null else PopoverType.HIGHLIGHTER
                    } else { viewModel.setActiveTool(ActiveTool.HIGHLIGHTER); activePopover = PopoverType.HIGHLIGHTER }
                }
                ToolDockIconButton(Icons.Default.AutoFixNormal, "Eraser", activeTool == ActiveTool.ERASER, Color(0xFFEF4444), colors) {
                    tick()
                    if (activeTool == ActiveTool.ERASER) {
                        activePopover = if (activePopover == PopoverType.ERASER) null else PopoverType.ERASER
                    } else { viewModel.setActiveTool(ActiveTool.ERASER); activePopover = PopoverType.ERASER }
                }
                ToolDockIconButton(Icons.Default.Gesture, "Lasso", activeTool == ActiveTool.LASSO, Color(0xFFA855F7), colors) {
                    tick(); viewModel.setActiveTool(ActiveTool.LASSO); activePopover = null
                }
                ToolDockIconButton(Icons.Default.Category, "Insert shape or text", activeTool == ActiveTool.SHAPE || activeTool == ActiveTool.TEXT, Color(0xFF10B981), colors) {
                    tick()
                    activePopover = if (activePopover == PopoverType.INSERT) null else PopoverType.INSERT
                }
            }
            val utility: @Composable () -> Unit = {
                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp), color = colors.divider)
                DockIconButton(Icons.AutoMirrored.Filled.Undo, "Undo", if (canUndo) colors.onPanel else colors.onPanelMuted.copy(alpha = 0.35f), canUndo) { viewModel.undo() }
                DockIconButton(Icons.AutoMirrored.Filled.Redo, "Redo", if (canRedo) colors.onPanel else colors.onPanelMuted.copy(alpha = 0.35f), canRedo) { viewModel.redo() }
                DockIconButton(Icons.Default.Settings, "Settings", colors.onPanel, true) { tick(); isSettingsOpen = true }
            }


            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (settings.leftHanded) { utility(); tools() } else { tools(); utility() }
                }
            }
        }
    }



        // 5. HANDWRITING RECOGNITION DIALOG
        if (recognitionResult != null || isRecognizing) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissRecognitionDialog() },
                title = { Text("Handwriting to text") },
                text = {
                    if (isRecognizing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Recognizing handwriting…")
                        }
                    } else {
                        var editableText by remember { mutableStateOf(recognitionResult ?: "") }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Recognized result:")
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { viewModel.dismissRecognitionDialog() }) { Text("Cancel") }
                                Button(onClick = { viewModel.confirmHandwritingToText(editableText) }) { Text("Replace ink") }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // 6. TEXT ADD/PLACE DIALOG
        if (isTextDialogOpen) {
            AlertDialog(
                onDismissRequest = { isTextDialogOpen = false },
                title = { Text("Add text", color = colors.onPanel) },
                text = {
                    OutlinedTextField(
                        value = newTypedText,
                        onValueChange = { newTypedText = it },
                        placeholder = { Text("Type note text…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.addTextElement(newTypedText, pendingTextPos.first, pendingTextPos.second)
                        newTypedText = ""
                        isTextDialogOpen = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { isTextDialogOpen = false }) { Text("Cancel", color = colors.onPanelMuted) }
                },
                containerColor = colors.panel
            )
        }

        // 6b. RENAME NOTE DIALOG
        if (isRenameDialogOpen) {
            AlertDialog(
                onDismissRequest = { isRenameDialogOpen = false },
                title = { Text("Rename Note", color = colors.onPanel) },
                text = {
                    OutlinedTextField(
                        value = editedTitleText,
                        onValueChange = { editedTitleText = it },
                        singleLine = true,
                        placeholder = { Text("Enter note title…") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onPanel,
                            unfocusedTextColor = colors.onPanel
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalTitle = editedTitleText.trim().ifBlank { "Untitled Note" }
                            viewModel.setNoteTitle(finalTitle)
                            isRenameDialogOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { isRenameDialogOpen = false }) { Text("Cancel", color = colors.onPanelMuted) }
                },
                containerColor = colors.panel
            )
        }


        // 7. SETTINGS SHEET
        if (isSettingsOpen) {
            ModalBottomSheet(
                onDismissRequest = { isSettingsOpen = false },
                containerColor = colors.panel
            ) {
                SettingsSheetContent(
                    settings = settings,
                    backgroundType = backgroundType,
                    colors = colors,
                    onBackground = { viewModel.setBackgroundType(it) },
                    onUpdate = { viewModel.updateSettings(it) }
                )
            }
        }

        // 8. QUICK COMMAND PALETTE DIALOG
        if (isCommandPaletteOpen) {
            CommandPaletteDialog(
                colors = colors,
                onDismiss = { viewModel.setCommandPaletteOpen(false) },
                onFocusMode = { viewModel.setFocusMode(true); viewModel.setCommandPaletteOpen(false) },
                onFitContent = { canvasViewRef?.fitToContent(); viewModel.setCommandPaletteOpen(false) },
                onResetZoom = { canvasViewRef?.resetView(); viewModel.setCommandPaletteOpen(false) },
                onUnlockAll = { viewModel.unlockAllObjects(); viewModel.setCommandPaletteOpen(false) },
                onExport = { exportAndShare(context, canvasViewRef); viewModel.setCommandPaletteOpen(false) },
                onClearCanvas = { viewModel.clearCanvas(); viewModel.setCommandPaletteOpen(false) },
                onOpenSettings = { isSettingsOpen = true; viewModel.setCommandPaletteOpen(false) },
                onSetCanvasStyle = { style -> viewModel.updateSettings { it.copy(canvasStyle = style) }; viewModel.setCommandPaletteOpen(false) },
                onSetBackground = { bg -> viewModel.setBackgroundType(bg); viewModel.setCommandPaletteOpen(false) }
            )
        }
    }
}

@Composable
private fun CommandPaletteDialog(
    colors: AppColors,
    onDismiss: () -> Unit,
    onFocusMode: () -> Unit,
    onFitContent: () -> Unit,
    onResetZoom: () -> Unit,
    onUnlockAll: () -> Unit,
    onExport: () -> Unit,
    onClearCanvas: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetCanvasStyle: (CanvasStyle) -> Unit,
    onSetBackground: (BackgroundType) -> Unit
) {
    var searchFilter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.accent)
                Text("Command & Quick Actions", color = colors.onPanel, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Search action or setting…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CommandRow("Focus Mode (Fullscreen)", Icons.Default.Fullscreen, colors, onFocusMode)
                    CommandRow("Fit All to Content", Icons.Default.FitScreen, colors, onFitContent)
                    CommandRow("Reset Zoom (100%)", Icons.Default.CenterFocusStrong, colors, onResetZoom)
                    CommandRow("Unlock All Objects", Icons.Default.LockOpen, colors, onUnlockAll)
                    CommandRow("Settings & Appearance", Icons.Default.Settings, colors, onOpenSettings)
                    CommandRow("Export Canvas Image", Icons.Default.Share, colors, onExport)
                    CommandRow("Surface: White Paper", Icons.Default.Palette, colors) { onSetCanvasStyle(CanvasStyle.WHITE) }
                    CommandRow("Surface: Warm Paper", Icons.Default.Palette, colors) { onSetCanvasStyle(CanvasStyle.PAPER) }
                    CommandRow("Surface: AMOLED Black", Icons.Default.Palette, colors) { onSetCanvasStyle(CanvasStyle.OLED) }
                    CommandRow("Pattern: Grid", Icons.Default.GridView, colors) { onSetBackground(BackgroundType.GRID) }
                    CommandRow("Pattern: Dots", Icons.Default.Grain, colors) { onSetBackground(BackgroundType.DOTS) }
                    CommandRow("Clear Canvas", Icons.Default.DeleteSweep, colors, onClearCanvas)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.onPanelMuted) } },
        containerColor = colors.panel
    )
}

@Composable
private fun CommandRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: AppColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.onPanel)
    }
}


enum class PopoverType { PEN, HIGHLIGHTER, ERASER, INSERT }

private fun exportAndShare(context: Context, view: StylusCanvasView?) {
    val bitmap = view?.exportToBitmap()
    if (bitmap == null) {
        Toast.makeText(context, "Nothing to export yet", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "note_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share note"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ToolDockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    colors: AppColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (isSelected) accentColor else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else colors.onPanelMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun DockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ColorSwatch(colorArgb: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(colorArgb))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF3B82F6) else Color(0x55FFFFFF), CircleShape)
            .clickable { onClick() }
    )
}

@Composable
fun PenPopoverContent(viewModel: DrawingViewModel, penConfig: PenConfig, recentColors: List<Long>, colors: AppColors) {
    Column(modifier = Modifier.padding(16.dp).widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pen", style = MaterialTheme.typography.titleSmall, color = colors.onPanel)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Size", style = MaterialTheme.typography.labelMedium, color = colors.onPanelMuted)
            Slider(
                value = penConfig.baseWidth,
                onValueChange = { viewModel.setPenWidth(it) },
                valueRange = 1f..30f,
                modifier = Modifier.weight(1f)
            )
            Text("${penConfig.baseWidth.toInt()}pt", style = MaterialTheme.typography.labelMedium, color = colors.onPanel, modifier = Modifier.width(38.dp))
        }
        Text("Color", style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
        FlowGrid(PEN_PALETTE, penConfig.color) { viewModel.setColor(it) }
        if (recentColors.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recentColors.forEach { c -> ColorSwatch(c, penConfig.color == c) { viewModel.setColor(c) } }
            }
        }
    }
}

@Composable
private fun FlowGrid(palette: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        palette.chunked(6).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowColors.forEach { c -> ColorSwatch(c, selected == c) { onPick(c) } }
            }
        }
    }
}

@Composable
fun HighlighterPopoverContent(viewModel: DrawingViewModel, color: Long, width: Float, colors: AppColors) {
    Column(modifier = Modifier.padding(16.dp).widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Highlighter", style = MaterialTheme.typography.titleSmall, color = colors.onPanel)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Size", style = MaterialTheme.typography.labelMedium, color = colors.onPanelMuted)
            Slider(
                value = width,
                onValueChange = { viewModel.setHighlighterWidth(it) },
                valueRange = 10f..50f,
                modifier = Modifier.weight(1f)
            )
            Text("${width.toInt()}pt", style = MaterialTheme.typography.labelMedium, color = colors.onPanel, modifier = Modifier.width(38.dp))
        }
        Text("Color", style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HIGHLIGHTER_PALETTE.forEach { c -> ColorSwatch(c, color == c) { viewModel.setHighlighterColor(c) } }
        }
    }
}

@Composable
fun EraserPopoverContent(viewModel: DrawingViewModel, mode: EraserMode, tempEraserSize: Float, colors: AppColors) {
    Column(modifier = Modifier.padding(16.dp).widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Eraser", style = MaterialTheme.typography.titleSmall, color = colors.onPanel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == EraserMode.PRECISION, onClick = { viewModel.setEraserMode(EraserMode.PRECISION) }, label = { Text("Pixel") })
            FilterChip(selected = mode == EraserMode.STROKE, onClick = { viewModel.setEraserMode(EraserMode.STROKE) }, label = { Text("Whole stroke") })
        }
        Text("Size (also used by S Pen button)", style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Slider(
                value = tempEraserSize,
                onValueChange = { v -> viewModel.updateSettings { it.copy(tempEraserSize = v) } },
                valueRange = 20f..120f,
                modifier = Modifier.weight(1f)
            )
            Text("${tempEraserSize.toInt()}", style = MaterialTheme.typography.labelMedium, color = colors.onPanel, modifier = Modifier.width(32.dp))
        }
    }
}

@Composable
fun InsertPopoverContent(
    viewModel: DrawingViewModel,
    selectedShape: ShapeType,
    activeTool: ActiveTool,
    colors: AppColors,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Insert", style = MaterialTheme.typography.titleSmall, color = colors.onPanel)
        Text("Shape (drag on canvas)", style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShapeType.entries.forEach { shape ->
                FilterChip(
                    selected = activeTool == ActiveTool.SHAPE && selectedShape == shape,
                    onClick = { viewModel.setSelectedShapeType(shape) },
                    label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        HorizontalDivider(color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.setActiveTool(ActiveTool.TEXT); onClose() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.TextFields, contentDescription = null, tint = if (activeTool == ActiveTool.TEXT) colors.accent else colors.onPanel)
            Column {
                Text("Text box", style = MaterialTheme.typography.bodyMedium, color = colors.onPanel)
                Text("Then tap where you want it", style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
            }
        }
    }
}

@Composable
private fun SettingsSheetContent(
    settings: AppSettings,
    backgroundType: BackgroundType,
    colors: AppColors,
    onBackground: (BackgroundType) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings & appearance", style = MaterialTheme.typography.headlineSmall, color = colors.onPanel, fontWeight = FontWeight.SemiBold)

        SettingsSectionLabel("Writing", colors)
        Text("Stroke smoothing", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = listOf(
                SmoothingLevel.OFF to "Off",
                SmoothingLevel.STANDARD to "Standard",
                SmoothingLevel.EXTRA to "Extra"
            ),
            selected = settings.strokeSmoothing,
            colors = colors
        ) { v -> onUpdate { it.copy(strokeSmoothing = v) } }
        SettingToggle("Pressure sensitivity", "Vary stroke width with tip pressure", settings.pressureSensitivity, colors) { v -> onUpdate { it.copy(pressureSensitivity = v) } }

        SettingsSectionLabel("S Pen", colors)
        SettingToggle("Side button erases", "Hold the S Pen button to erase, release to resume", settings.sidePenButtonErases, colors) { v -> onUpdate { it.copy(sidePenButtonErases = v) } }
        SettingSlider("Eraser size", "${settings.tempEraserSize.toInt()} px", settings.tempEraserSize, 20f..120f, colors) { v -> onUpdate { it.copy(tempEraserSize = v) } }

        SettingsSectionLabel("Shapes", colors)
        SettingToggle("Snap shapes on hold", "Draw a rough shape, pause at the end to clean it up", settings.shapeSnapOnHold, colors) { v -> onUpdate { it.copy(shapeSnapOnHold = v) } }
        Text("Recognition sensitivity", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = listOf(
                ShapeSensitivity.LOW to "Low",
                ShapeSensitivity.MEDIUM to "Medium",
                ShapeSensitivity.HIGH to "High"
            ),
            selected = settings.shapeSensitivity,
            colors = colors
        ) { v -> onUpdate { it.copy(shapeSensitivity = v) } }

        SettingsSectionLabel("Canvas Surface & Grid", colors)
        Text("Surface background", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = listOf(
                CanvasStyle.CHARCOAL to "Charcoal",
                CanvasStyle.WHITE to "White",
                CanvasStyle.PAPER to "Warm Paper",
                CanvasStyle.OLED to "AMOLED Black"
            ),
            selected = settings.canvasStyle,
            colors = colors
        ) { style -> onUpdate { it.copy(canvasStyle = style) } }
        Text("Grid pattern", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = BackgroundType.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selected = backgroundType,
            colors = colors,
            onSelect = onBackground
        )

        SettingsSectionLabel("App Chrome Theme", colors)
        Text("Theme preset", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipRow(
                options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark"
                ),
                selected = settings.themeMode,
                colors = colors
            ) { mode -> onUpdate { it.copy(themeMode = mode) } }
            ChipRow(
                options = listOf(
                    ThemeMode.WARM_PAPER to "Warm Paper",
                    ThemeMode.TRUE_BLACK to "True Black",
                    ThemeMode.SOFT_GRAY to "Soft Gray"
                ),
                selected = settings.themeMode,
                colors = colors
            ) { mode -> onUpdate { it.copy(themeMode = mode) } }
        }
        SettingSlider("Toolbar transparency", "${((1f - settings.toolbarOpacity) * 100).toInt()}%", settings.toolbarOpacity, 0.45f..1f, colors) { v -> onUpdate { it.copy(toolbarOpacity = v) } }


        SettingsSectionLabel("Ergonomics", colors)
        SettingToggle("Draw with finger", "Off = one finger pans the canvas (pen-first)", settings.drawWithFinger, colors) { v -> onUpdate { it.copy(drawWithFinger = v) } }
        SettingToggle("Left-handed dock", "Mirror the tool dock layout", settings.leftHanded, colors) { v -> onUpdate { it.copy(leftHanded = v) } }
        SettingToggle("Haptic feedback", "Subtle tick on tool changes", settings.hapticFeedback, colors) { v -> onUpdate { it.copy(hapticFeedback = v) } }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: AppColors,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = colors.onPanelMuted)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingsSectionLabel(text: String, colors: AppColors) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    colors: AppColors,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: AppColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
