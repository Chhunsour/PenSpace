package com.spen.canvas.model

import java.util.UUID

/**
 * Complete serializable snapshot of canvas document.
 */
data class CanvasDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Note",
    val strokes: List<InkStroke> = emptyList(),
    val shapes: List<ShapeElement> = emptyList(),
    val textElements: List<TextElement> = emptyList(),
    val images: List<ImageElement> = emptyList(),
    val backgroundType: BackgroundType = BackgroundType.PLAIN,

    val canvasStyle: CanvasStyle = CanvasStyle.CHARCOAL,
    val zoomScale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)


