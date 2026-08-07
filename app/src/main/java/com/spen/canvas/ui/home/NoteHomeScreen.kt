package com.spen.canvas.ui.home

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spen.canvas.model.BackgroundType
import com.spen.canvas.model.CanvasDocument
import com.spen.canvas.ui.DrawingViewModel
import com.spen.canvas.ui.NoteSortOrder
import com.spen.canvas.ui.theme.resolveAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteHomeScreen(
    viewModel: DrawingViewModel,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val notesList by viewModel.notesList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterFavoritesOnly by viewModel.filterFavoritesOnly.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val colors = resolveAppColors(settings, systemDark)
    val glass = colors.panel.copy(alpha = settings.toolbarOpacity)
    val glassBorder = colors.panelBorder.copy(alpha = (colors.panelBorder.alpha * 1.4f).coerceAtMost(1f))

    val showTrashTab by viewModel.showTrashTab.collectAsState()

    var noteToRename by remember { mutableStateOf<CanvasDocument?>(null) }
    var renameText by remember { mutableStateOf("") }
    var noteToDelete by remember { mutableStateOf<CanvasDocument?>(null) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    val activeNotes = remember(notesList) { notesList.filter { !it.isDeleted } }
    val recentNotes = remember(activeNotes) {
        activeNotes.sortedByDescending { it.lastModified }.take(5)
    }

    // Filter & Sort notes for library
    val filteredNotes = remember(notesList, searchQuery, filterFavoritesOnly, showTrashTab, sortOrder) {
        notesList.filter { doc ->
            val matchesTrash = if (showTrashTab) doc.isDeleted else !doc.isDeleted
            val matchesQuery = searchQuery.isBlank() || doc.title.contains(searchQuery, ignoreCase = true)
            val matchesFav = showTrashTab || !filterFavoritesOnly || doc.isFavorite
            matchesTrash && matchesQuery && matchesFav
        }.let { list ->
            when (sortOrder) {
                NoteSortOrder.LAST_MODIFIED -> list.sortedWith(compareByDescending<CanvasDocument> { it.isFavorite }.thenByDescending { it.lastModified })
                NoteSortOrder.TITLE -> list.sortedWith(compareByDescending<CanvasDocument> { it.isFavorite }.thenBy { it.title.lowercase() })
                NoteSortOrder.CREATED_AT -> list.sortedWith(compareByDescending<CanvasDocument> { it.isFavorite }.thenByDescending { it.createdAt })
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(settings.canvasColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            // Workspace Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = "InkFlow",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPanel
                        )
                        Text(
                            text = "S Pen Studio Workspace",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPanelMuted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass)
                            .border(1.dp, glassBorder, CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.onPanel)
                    }
                }
            }

            // Quick Note CTA Hero Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = glass,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, glassBorder, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Quick Note",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPanel
                            )
                            Text(
                                text = "Take out S Pen & write immediately",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onPanelMuted
                            )
                        }

                        Button(
                            onClick = { viewModel.createNewNote(BackgroundType.PLAIN) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Note", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Template Shortcuts Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TemplateChip(
                            label = "Plain",
                            icon = Icons.Default.CropSquare,
                            colors = colors,
                            onClick = { viewModel.createNewNote(BackgroundType.PLAIN) }
                        )
                        TemplateChip(
                            label = "Grid",
                            icon = Icons.Default.GridView,
                            colors = colors,
                            onClick = { viewModel.createNewNote(BackgroundType.GRID) }
                        )
                        TemplateChip(
                            label = "Dots",
                            icon = Icons.Default.Grain,
                            colors = colors,
                            onClick = { viewModel.createNewNote(BackgroundType.DOTS) }
                        )
                        TemplateChip(
                            label = "Lines",
                            icon = Icons.Default.FormatAlignLeft,
                            colors = colors,
                            onClick = { viewModel.createNewNote(BackgroundType.LINES) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Continue Working (Recent Notes Carousel)
            if (!showTrashTab && searchQuery.isBlank() && !filterFavoritesOnly && recentNotes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Continue Working",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPanelMuted
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentNotes, key = { "recent_${it.id}" }) { note ->
                            RecentNoteCard(
                                note = note,
                                glass = glass,
                                glassBorder = glassBorder,
                                colors = colors,
                                onOpen = { viewModel.openNote(note.id) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Notes Library Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showTrashTab) "Trash Bin" else "Notes Library",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPanelMuted
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { isSortMenuExpanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = colors.onPanelMuted)
                        }
                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false },
                            modifier = Modifier.background(colors.panel)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Last modified", color = colors.onPanel) },
                                leadingIcon = { if (sortOrder == NoteSortOrder.LAST_MODIFIED) Icon(Icons.Default.Check, null, tint = colors.accent) },
                                onClick = { viewModel.setSortOrder(NoteSortOrder.LAST_MODIFIED); isSortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Title", color = colors.onPanel) },
                                leadingIcon = { if (sortOrder == NoteSortOrder.TITLE) Icon(Icons.Default.Check, null, tint = colors.accent) },
                                onClick = { viewModel.setSortOrder(NoteSortOrder.TITLE); isSortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Date created", color = colors.onPanel) },
                                leadingIcon = { if (sortOrder == NoteSortOrder.CREATED_AT) Icon(Icons.Default.Check, null, tint = colors.accent) },
                                onClick = { viewModel.setSortOrder(NoteSortOrder.CREATED_AT); isSortMenuExpanded = false }
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.setGridView(!isGridView) }) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = colors.onPanelMuted
                        )
                    }
                }
            }

            // Search Bar & Filter Chips
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = glass,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, glassBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.onPanelMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search notes...", color = colors.onPanelMuted.copy(alpha = 0.7f), fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = colors.onPanel,
                            unfocusedTextColor = colors.onPanel
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.onPanelMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Filter Tabs (All, Favorites, Trash)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showTrashTab && !filterFavoritesOnly,
                    onClick = { viewModel.setShowTrashTab(false); viewModel.setFilterFavoritesOnly(false) },
                    label = { Text("All (${notesList.count { !it.isDeleted }})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent,
                        selectedLabelColor = Color.White,
                        containerColor = glass,
                        labelColor = colors.onPanel
                    )
                )
                FilterChip(
                    selected = !showTrashTab && filterFavoritesOnly,
                    onClick = { viewModel.setShowTrashTab(false); viewModel.setFilterFavoritesOnly(true) },
                    label = { Text("Favorites", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            if (filterFavoritesOnly) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent,
                        selectedLabelColor = Color.White,
                        containerColor = glass,
                        labelColor = colors.onPanel
                    )
                )
                FilterChip(
                    selected = showTrashTab,
                    onClick = { viewModel.setShowTrashTab(true) },
                    label = { Text("Trash (${notesList.count { it.isDeleted }})", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.danger,
                        selectedLabelColor = Color.White,
                        containerColor = glass,
                        labelColor = colors.onPanel
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // Notes List / Grid
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = colors.onPanelMuted.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No notes match your search" else if (showTrashTab) "Trash is empty" else "No notes yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onPanelMuted
                        )
                        if (!showTrashTab && searchQuery.isBlank()) {
                            Text(
                                text = "Tap + New Note to start writing with S Pen",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onPanelMuted.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            glass = glass,
                            glassBorder = glassBorder,
                            colors = colors,
                            onOpen = { if (!note.isDeleted) viewModel.openNote(note.id) },
                            onToggleFavorite = { viewModel.toggleFavoriteNote(note.id) },
                            onRename = { noteToRename = note; renameText = note.title },
                            onDuplicate = { viewModel.duplicateNote(note.id) },
                            onRestore = { viewModel.restoreNoteFromTrash(note.id) },
                            onDelete = {
                                if (note.isDeleted) {
                                    viewModel.purgeNotePermanently(note.id)
                                } else {
                                    noteToDelete = note
                                }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            glass = glass,
                            glassBorder = glassBorder,
                            colors = colors,
                            onOpen = { if (!note.isDeleted) viewModel.openNote(note.id) },
                            onToggleFavorite = { viewModel.toggleFavoriteNote(note.id) },
                            onRename = { noteToRename = note; renameText = note.title },
                            onDuplicate = { viewModel.duplicateNote(note.id) },
                            onRestore = { viewModel.restoreNoteFromTrash(note.id) },
                            onDelete = {
                                if (note.isDeleted) {
                                    viewModel.purgeNotePermanently(note.id)
                                } else {
                                    noteToDelete = note
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    noteToRename?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToRename = null },
            title = { Text("Rename Note", color = colors.onPanel) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onPanel,
                        unfocusedTextColor = colors.onPanel
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.openNote(note.id)
                        viewModel.setNoteTitle(renameText)
                    }
                    noteToRename = null
                }) { Text("Save", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { noteToRename = null }) { Text("Cancel", color = colors.onPanelMuted) }
            },
            containerColor = colors.panel
        )
    }

    // Delete Confirmation Dialog
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Move to Trash?", color = colors.onPanel) },
            text = { Text("Are you sure you want to move '${note.title}' to Trash?", color = colors.onPanelMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) { Text("Move to Trash") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel", color = colors.onPanelMuted) }
            },
            containerColor = colors.panel
        )
    }
}

@Composable
private fun TemplateChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: com.spen.canvas.ui.theme.AppColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.panelBorder.copy(alpha = 0.25f),
        modifier = Modifier
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colors.onPanel, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onPanel)
        }
    }
}

@Composable
private fun RecentNoteCard(
    note: CanvasDocument,
    glass: Color,
    glassBorder: Color,
    colors: com.spen.canvas.ui.theme.AppColors,
    onOpen: () -> Unit
) {
    val relativeTime = remember(note.lastModified) {
        DateUtils.getRelativeTimeSpanString(
            note.lastModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = glass,
        modifier = Modifier
            .width(160.dp)
            .height(140.dp)
            .border(1.dp, glassBorder, RoundedCornerShape(18.dp))
            .clickable { onOpen() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color(0xFF0F172A))
            ) {
                NoteThumbnailImage(note = note, modifier = Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPanel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPanelMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: CanvasDocument,
    glass: Color,
    glassBorder: Color,
    colors: com.spen.canvas.ui.theme.AppColors,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onRestore: () -> Unit = {},
    onDelete: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val relativeTime = remember(note.lastModified) {
        DateUtils.getRelativeTimeSpanString(
            note.lastModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = glass,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
            .clickable { onOpen() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Visual Canvas Thumbnail Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFF0F172A))
            ) {
                NoteThumbnailImage(note = note, modifier = Modifier.fillMaxSize())

                if (note.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Note Meta Info
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPanel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = relativeTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPanelMuted,
                            fontSize = 11.sp
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { isMenuOpen = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = colors.onPanelMuted)
                        }
                        DropdownMenu(
                            expanded = isMenuOpen,
                            onDismissRequest = { isMenuOpen = false },
                            modifier = Modifier.background(colors.panel)
                        ) {
                            if (note.isDeleted) {
                                DropdownMenuItem(
                                    text = { Text("Restore note", color = colors.accent) },
                                    leadingIcon = { Icon(Icons.Default.Restore, null, tint = colors.accent) },
                                    onClick = { onRestore(); isMenuOpen = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete permanently", color = colors.danger) },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = colors.danger) },
                                    onClick = { onDelete(); isMenuOpen = false }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(if (note.isFavorite) "Unfavorite" else "Favorite", color = colors.onPanel) },
                                    leadingIcon = { Icon(if (note.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, null, tint = colors.onPanel) },
                                    onClick = { onToggleFavorite(); isMenuOpen = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename", color = colors.onPanel) },
                                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = colors.onPanel) },
                                    onClick = { onRename(); isMenuOpen = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate", color = colors.onPanel) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = colors.onPanel) },
                                    onClick = { onDuplicate(); isMenuOpen = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Trash", color = colors.danger) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.danger) },
                                    onClick = { onDelete(); isMenuOpen = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: CanvasDocument,
    glass: Color,
    glassBorder: Color,
    colors: com.spen.canvas.ui.theme.AppColors,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onRestore: () -> Unit = {},
    onDelete: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val relativeTime = remember(note.lastModified) {
        DateUtils.getRelativeTimeSpanString(
            note.lastModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = glass,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(1.dp, glassBorder, RoundedCornerShape(16.dp))
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
            ) {
                NoteThumbnailImage(note = note, modifier = Modifier.fillMaxSize())
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPanel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$relativeTime • ${note.strokes.size} strokes",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPanelMuted
                )
            }

            Row {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (note.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (note.isFavorite) Color(0xFFFACC15) else colors.onPanelMuted
                    )
                }

                Box {
                    IconButton(onClick = { isMenuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = colors.onPanelMuted)
                    }
                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false },
                        modifier = Modifier.background(colors.panel)
                    ) {
                        if (note.isDeleted) {
                            DropdownMenuItem(
                                text = { Text("Restore note", color = colors.accent) },
                                leadingIcon = { Icon(Icons.Default.Restore, null, tint = colors.accent) },
                                onClick = { onRestore(); isMenuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete permanently", color = colors.danger) },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = colors.danger) },
                                onClick = { onDelete(); isMenuOpen = false }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Rename", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = colors.onPanel) },
                                onClick = { onRename(); isMenuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate", color = colors.onPanel) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = colors.onPanel) },
                                onClick = { onDuplicate(); isMenuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Trash", color = colors.danger) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.danger) },
                                onClick = { onDelete(); isMenuOpen = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteThumbnailImage(
    note: CanvasDocument,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(note.id, note.lastModified) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(note.id, note.lastModified) {
        withContext(Dispatchers.IO) {
            val bmp = NoteThumbnailGenerator.getThumbnailBitmap(context, note)
            withContext(Dispatchers.Main) {
                bitmap = bmp
            }
        }
    }

    val currentBmp = bitmap
    if (currentBmp != null) {
        Image(
            bitmap = currentBmp.asImageBitmap(),
            contentDescription = note.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Gesture,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
