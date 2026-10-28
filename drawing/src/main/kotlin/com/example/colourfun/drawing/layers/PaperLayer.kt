package com.example.colourfun.drawing.layers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.colourfun.drawing.document.DocumentSize

/** The background. Never stored per picture and never erased. Plain colour until textures exist. */
class PaperLayer(val color: Int = Color.WHITE) {
    private val paint = Paint().also { it.color = color }

    fun draw(canvas: Canvas, size: DocumentSize) {
        canvas.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), paint)
    }
}
