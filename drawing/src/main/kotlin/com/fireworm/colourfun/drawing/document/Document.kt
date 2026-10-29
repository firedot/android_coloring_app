package com.fireworm.colourfun.drawing.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.fireworm.colourfun.drawing.layers.OutlineLayer
import com.fireworm.colourfun.drawing.layers.PaintLayer
import com.fireworm.colourfun.drawing.layers.PaperLayer
import com.fireworm.colourfun.drawing.layers.StrokeLayer

/**
 * How to show the stroke in progress: [paint] draws the scratch layer over [bounds] exactly as the
 * merge into the paint layer will. [isolated] when its blend mode must act on the paint layer
 * alone (e.g. erasing with DstOut), not on the paper below.
 */
class StrokePreview(val paint: Paint, val bounds: Rect, val isolated: Boolean)

/**
 * One picture: paper, paint, stroke scratch and outline layers of a fixed size.
 * Once created, the document and its bitmaps belong to the main thread.
 */
class Document(val size: DocumentSize, outlineBitmap: Bitmap) {
    val paper = PaperLayer()
    val paint = PaintLayer(size)
    val stroke = StrokeLayer(size)
    val outline = OutlineLayer(outlineBitmap, size)

    private val layerBounds = RectF()

    /** Composes paper → paint (+ stroke [preview]) → outline in document coordinates. */
    fun draw(canvas: Canvas, preview: StrokePreview? = null) {
        paper.draw(canvas, size)
        when {
            preview == null -> paint.draw(canvas)
            !preview.isolated -> {
                // SrcOver is associative: drawing the stroke over paper + paint equals merging it first.
                paint.draw(canvas)
                canvas.drawRect(preview.bounds, preview.paint)
            }
            else -> {
                canvas.withSave {
                    clipOutRect(preview.bounds)
                    paint.draw(this)
                }
                // Inside the stroke, merge into a copy of the paint first, then put that on the paper.
                layerBounds.set(preview.bounds)
                val layer = canvas.saveLayer(layerBounds, null)
                paint.draw(canvas)
                canvas.drawRect(preview.bounds, preview.paint)
                canvas.restoreToCount(layer)
            }
        }
        outline.draw(canvas)
    }

    /**
     * The picture rectangle only, flattened (paper → paint → outline): what is saved to photos.
     * The side margins of a wide screen are left out, so every export has the picture's 4:3 shape.
     */
    fun flattenPicture(): Bitmap {
        val image = createBitmap(size.pictureWidth, size.pictureHeight)
        val canvas = Canvas(image)
        canvas.translate(-size.pictureLeft.toFloat(), -size.pictureTop.toFloat())
        draw(canvas)
        return image
    }
}
