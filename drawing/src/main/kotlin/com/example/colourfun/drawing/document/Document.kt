package com.example.colourfun.drawing.document

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.colourfun.drawing.layers.OutlineLayer
import com.example.colourfun.drawing.layers.PaintLayer
import com.example.colourfun.drawing.layers.PaperLayer

/**
 * One picture: paper, paint and outline layers of a fixed size.
 * Once created, the document and its bitmaps belong to the main thread.
 */
class Document(val size: DocumentSize, outlineBitmap: Bitmap) {
    val paper = PaperLayer()
    val paint = PaintLayer(size)
    val outline = OutlineLayer(outlineBitmap, size)

    /** Composes paper → paint → outline in document coordinates. */
    fun draw(canvas: Canvas) {
        paper.draw(canvas, size)
        paint.draw(canvas)
        outline.draw(canvas)
    }
}
