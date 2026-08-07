package com.spen.canvas.model

/**
 * Complete serializable snapshot of canvas document.
 */
data class CanvasDocument(
    val title: String = "Untitled Note",
    val strokes: List<InkStroke> = emptyList(),
    val shapes: List<ShapeElement> = emptyList(),
    val textElements: List<TextElement> = emptyList(),
    val backgroundType: BackgroundType = BackgroundType.PLAIN,
    val zoomScale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val lastModified: Long = System.currentTimeMillis()
)
