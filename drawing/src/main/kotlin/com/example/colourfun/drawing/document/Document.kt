package com.example.colourfun.drawing.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.colourfun.drawing.layers.OutlineLayer
import com.example.colourfun.drawing.layers.PaintLayer
import com.example.colourfun.drawing.layers.PaperLayer
import com.example.colourfun.drawing.layers.StrokeLayer

/**
 * One picture: paper, paint, stroke scratch and outline layers of a fixed size.
 * Once created, the document and its bitmaps belong to the main thread.
 */
class Document(val size: DocumentSize, outlineBitmap: Bitmap) {
    val paper = PaperLayer()
    val paint = PaintLayer(size)
    val stroke = StrokeLayer(size)
    val outline = OutlineLayer(outlineBitmap, size)

    /**
     * Composes paper → paint → stroke → outline in document coordinates. The in-progress stroke is
     * drawn with [strokePaint] (brush opacity and blend mode), or skipped when it is `null`.
     */
    fun draw(canvas: Canvas, strokePaint: Paint? = null) {
        paper.draw(canvas, size)
        paint.draw(canvas)
        if (strokePaint != null) canvas.drawBitmap(stroke.bitmap, 0f, 0f, strokePaint)
        outline.draw(canvas)
    }
}
