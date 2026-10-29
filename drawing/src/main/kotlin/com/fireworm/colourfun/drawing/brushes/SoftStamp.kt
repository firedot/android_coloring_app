package com.fireworm.colourfun.drawing.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap

/**
 * A soft round stamp: solid in the middle, fading to nothing at the edge. Drawn scaled and tinted,
 * so one small bitmap serves every size and colour.
 */
internal class SoftStamp {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dest = RectF()
    private var tint = 0

    fun draw(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        if (color != tint) {
            paint.colorFilter = PorterDuffColorFilter(color or (0xFF shl 24), PorterDuff.Mode.SRC_IN)
            tint = color
        }
        paint.alpha = (alpha * Color.alpha(color)).toInt().coerceIn(0, 255)
        dest.set(x - radius, y - radius, x + radius, y + radius)
        canvas.drawBitmap(Texture, null, dest, paint)
    }

    private companion object {
        const val SIZE = 64

        /** Created on first use (main thread only, like all drawing). */
        val Texture: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
            createBitmap(SIZE, SIZE).also { bitmap ->
                val r = SIZE / 2f
                val gradient = RadialGradient(
                    r, r, r,
                    intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
                Canvas(bitmap).drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient })
            }
        }
    }
}
