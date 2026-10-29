package com.fireworm.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.document.DocumentSize

/** Everything the child draws; the only layer that changes. Starts transparent. */
class PaintLayer(size: DocumentSize) {
    val bitmap: Bitmap = createBitmap(size.width, size.height)
    val canvas = Canvas(bitmap)

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    /** Replaces all paint with [saved]. A saved document is never resized, so the sizes must match. */
    fun load(saved: Bitmap) {
        require(saved.width == bitmap.width && saved.height == bitmap.height) {
            "Saved paint is ${saved.width}×${saved.height}, the document ${bitmap.width}×${bitmap.height}"
        }
        val replace = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        canvas.drawBitmap(saved, 0f, 0f, replace)
    }
}
