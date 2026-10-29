package com.fireworm.colourfun.drawing.history

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * An exact copy of a bitmap's pixels inside [bounds].
 *
 * Pixels are kept as raw premultiplied bytes so restoring is lossless (`getPixels`/`setPixels`
 * would round semi-transparent edges). They start uncompressed; [History] deflates them off the
 * main thread and hands the result back with [onCompressed]. Main thread only.
 */
class PixelPatch private constructor(val bounds: Rect, private val rawSize: Int, raw: ByteArray) {
    private var raw: ByteArray? = raw
    private var deflated: ByteArray? = null

    /** Size once compressed; 0 while compression is pending. */
    val compressedBytes: Int get() = deflated?.size ?: 0

    /** The uncompressed pixels while compression is pending, otherwise `null`. */
    internal val pendingRaw: ByteArray? get() = raw

    internal fun onCompressed(bytes: ByteArray) {
        deflated = bytes
        raw = null
    }

    /** Writes the stored pixels back into [target], replacing what is there. */
    fun writeTo(target: Bitmap) {
        // Inflating a stroke's bounds takes a few ms; fine for an undo tap.
        val bytes = raw ?: inflate(checkNotNull(deflated), rawSize)
        val tile = createBitmap(bounds.width(), bounds.height())
        tile.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        Canvas(target).drawBitmap(tile, bounds.left.toFloat(), bounds.top.toFloat(), copyPaint)
        tile.recycle()
    }

    companion object {
        private val copyPaint = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }

        fun capture(source: Bitmap, bounds: Rect): PixelPatch {
            val tile = Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
            val bytes = ByteArray(tile.byteCount)
            tile.copyPixelsToBuffer(ByteBuffer.wrap(bytes))
            if (tile !== source) tile.recycle()
            return PixelPatch(Rect(bounds), bytes.size, bytes)
        }

        /** Pure; safe to call off the main thread. Most of a stroke's bounds is unchanged and compresses well. */
        internal fun deflate(input: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.BEST_SPEED)
            try {
                deflater.setInput(input)
                deflater.finish()
                val out = ByteArrayOutputStream(input.size / 16 + 64)
                val buffer = ByteArray(64 * 1024)
                while (!deflater.finished()) {
                    out.write(buffer, 0, deflater.deflate(buffer))
                }
                return out.toByteArray()
            } finally {
                deflater.end()
            }
        }

        private fun inflate(input: ByteArray, size: Int): ByteArray {
            val inflater = Inflater()
            try {
                inflater.setInput(input)
                val out = ByteArray(size)
                var offset = 0
                while (offset < size && !inflater.finished()) {
                    offset += inflater.inflate(out, offset, size - offset)
                }
                check(offset == size) { "Corrupt pixel patch: $offset of $size bytes" }
                return out
            } finally {
                inflater.end()
            }
        }
    }
}
