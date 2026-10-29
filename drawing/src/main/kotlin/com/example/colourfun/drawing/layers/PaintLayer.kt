package com.example.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.example.colourfun.drawing.document.DocumentSize

/** Everything the child draws; the only layer that changes. Starts transparent. */
class PaintLayer(size: DocumentSize) {
    val bitmap: Bitmap = createBitmap(size.width, size.height)
    val canvas = Canvas(bitmap)

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }
}
