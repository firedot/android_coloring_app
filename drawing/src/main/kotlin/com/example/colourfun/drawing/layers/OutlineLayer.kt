package com.example.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.colourfun.drawing.document.DocumentSize

/** Black line art, always drawn on top so paint never covers it. Stored as alpha only. */
class OutlineLayer(val bitmap: Bitmap, size: DocumentSize) {
    init {
        require(bitmap.config == Bitmap.Config.ALPHA_8) { "Outline bitmap must be ALPHA_8" }
        require(bitmap.width == size.width && bitmap.height == size.height) {
            "Outline bitmap must match the document size"
        }
    }

    // An ALPHA_8 bitmap is drawn in the paint's colour.
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).also { it.color = Color.BLACK }

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    companion object {
        /** A pixel with outline alpha above this counts as line. */
        const val LINE_ALPHA_THRESHOLD = 96
    }
}
