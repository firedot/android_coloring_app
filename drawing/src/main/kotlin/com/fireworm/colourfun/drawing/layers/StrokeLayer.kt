package com.fireworm.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.document.DocumentSize

/** Scratch layer for the stroke in progress; merged into the paint layer when the stroke ends. */
class StrokeLayer(size: DocumentSize) {
    val bitmap: Bitmap = createBitmap(size.width, size.height)
    val canvas = Canvas(bitmap)

    private val clearPaint = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    fun clear(rect: Rect) {
        canvas.drawRect(rect, clearPaint)
    }
}
