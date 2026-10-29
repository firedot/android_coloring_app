package com.fireworm.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.regions.RegionLabeler
import java.io.IOException
import java.nio.ByteBuffer

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
        const val LINE_ALPHA_THRESHOLD = RegionLabeler.LINE_ALPHA_THRESHOLD

        /**
         * The widest line a library picture may have (PICTURE_GUIDELINES T4), in picture pixels.
         * Region masks are dilated by half of it, so paint always reaches under the whole line.
         */
        const val MAX_LINE_WIDTH = 18f

        /**
         * Decodes a library picture PNG (black lines on white) into a document-sized outline:
         * `alpha = 255 − luminance`, placed at the picture area and scaled with the document. The
         * side margins stay empty. Blocking: call it off the main thread.
         */
        fun decode(png: ByteArray, size: DocumentSize): Bitmap {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = BitmapFactory.decodeByteArray(png, 0, png.size, options)
                ?: throw IOException("Cannot decode the picture PNG")
            val picture = if (decoded.width == size.pictureWidth && decoded.height == size.pictureHeight) {
                decoded
            } else {
                decoded.scale(size.pictureWidth, size.pictureHeight).also { decoded.recycle() }
            }
            return fromPicture(picture, size).also { picture.recycle() }
        }

        /** [picture] must already be the size of the document's picture area. */
        internal fun fromPicture(picture: Bitmap, size: DocumentSize): Bitmap {
            require(picture.width == size.pictureWidth && picture.height == size.pictureHeight) {
                "Picture must be ${size.pictureWidth}×${size.pictureHeight}"
            }
            val outline = createBitmap(size.width, size.height, Bitmap.Config.ALPHA_8)
            val stride = outline.rowBytes
            val alpha = ByteArray(stride * size.height)
            val row = IntArray(picture.width)
            for (y in 0 until picture.height) {
                picture.getPixels(row, 0, picture.width, 0, y, picture.width, 1)
                val out = (size.pictureTop + y) * stride + size.pictureLeft
                for (x in row.indices) alpha[out + x] = (255 - luminance(row[x])).toByte()
            }
            outline.copyPixelsFromBuffer(ByteBuffer.wrap(alpha))
            return outline
        }

        /** Rec. 709 luma of an opaque pixel; transparent pixels count as white paper. */
        private fun luminance(argb: Int): Int {
            val a = argb ushr 24
            if (a == 0) return 255
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            return ((2126 * r + 7152 * g + 722 * b) / 10000).coerceIn(0, 255)
        }
    }
}
