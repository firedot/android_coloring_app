package com.fireworm.colourfun.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.document.DocumentSize
import java.io.ByteArrayOutputStream

/**
 * Hand-drawn test picture, encoded as a library-style 2048 × 1536 PNG: a body oval and two ears,
 * with a head circle drawn on top that overlaps the body and both ears. Coordinates are in
 * picture pixels; [docX]/[docY] map them into a document.
 */
object TestOutline {
    val BODY_CENTER = PointF(1024f, 1000f)
    const val BODY_RX = 520f
    const val BODY_RY = 340f

    val HEAD_CENTER = PointF(1024f, 560f)
    const val HEAD_RADIUS = 280f

    val LEFT_EAR_CENTER = PointF(830f, 330f)
    val RIGHT_EAR_CENTER = PointF(1218f, 330f)
    const val EAR_RX = 110f
    const val EAR_RY = 150f

    const val STROKE_WIDTH = 14f

    /** Black anti-aliased lines on white; each shape hides the lines behind it. */
    val png: ByteArray by lazy {
        val picture = createBitmap(DocumentSize.PICTURE_WIDTH, DocumentSize.PICTURE_HEIGHT)
        val canvas = Canvas(picture)
        canvas.drawColor(Color.WHITE)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH
        }
        val shapes = listOf(
            oval(BODY_CENTER, BODY_RX, BODY_RY),
            oval(LEFT_EAR_CENTER, EAR_RX, EAR_RY),
            oval(RIGHT_EAR_CENTER, EAR_RX, EAR_RY),
            oval(HEAD_CENTER, HEAD_RADIUS, HEAD_RADIUS),
        )
        for (shape in shapes) {
            canvas.drawOval(shape, fill)
            canvas.drawOval(shape, line)
        }
        ByteArrayOutputStream().use {
            check(picture.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }
    }

    fun docX(size: DocumentSize, x: Float): Float = size.pictureLeft + x * size.scale

    fun docY(size: DocumentSize, y: Float): Float = size.pictureTop + y * size.scale

    private fun oval(center: PointF, rx: Float, ry: Float) = RectF(center.x - rx, center.y - ry, center.x + rx, center.y + ry)
}
