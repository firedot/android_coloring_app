package com.fireworm.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
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

    /** Replaces all paint with [saved], scaled if it was made at another document size. */
    fun load(saved: Bitmap) {
        val replace = Paint(Paint.FILTER_BITMAP_FLAG).also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        canvas.drawBitmap(saved, null, Rect(0, 0, bitmap.width, bitmap.height), replace)
    }
}
