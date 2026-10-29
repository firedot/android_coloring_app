package com.fireworm.colourfun.drawing.regions

import android.graphics.Bitmap
import android.graphics.Rect
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * The closed areas of a picture, labelled from the rendered outline bitmap (never from vector
 * geometry, so it matches exactly what the child sees).
 *
 * Every pixel has a region id; `0` means outline. Labelled by [RegionLabeler]; the region touching
 * the document border is the background, which includes the side margins around the picture.
 * Built off the main thread; read-only after.
 */
class RegionMap private constructor(
    val width: Int,
    val height: Int,
    private val labels: ShortArray,
    /** Index `id - 1`. */
    val regions: List<Region>,
    /** Per region (index `id - 1`): outline pixels within the dilation radius of it. */
    private val halos: Array<IntArray>,
) {
    /**
     * @param bounds bounding box of the region's own pixels.
     * @param maskBounds bounding box of its dilated mask (the region plus the outline under its edge).
     * @param area number of the region's own pixels.
     * @param isBackground the region touches the document border.
     */
    class Region(
        val id: Int,
        val bounds: Rect,
        val maskBounds: Rect,
        val area: Int,
        val isBackground: Boolean,
    )

    fun region(id: Int): Region = regions[id - 1]

    /** The region at a pixel; `0` on the outline or outside the document. */
    fun regionAt(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) labels[y * width + x].toInt() else 0

    /**
     * The region under a document point. On the outline it picks the nearest region instead;
     * `0` only if no region is within [NEAREST_SEARCH_RADIUS] pixels.
     */
    fun regionNear(x: Float, y: Float): Int {
        val i = nearestRegionPixel(x, y)
        return if (i < 0) 0 else labels[i].toInt()
    }

    /**
     * Index (`y * width + x`) of the non-outline pixel nearest to a document point: the point
     * itself unless it is on the outline. `-1` if none is within [NEAREST_SEARCH_RADIUS] pixels.
     */
    internal fun nearestRegionPixel(x: Float, y: Float): Int {
        val cx = x.toInt().coerceIn(0, width - 1)
        val cy = y.toInt().coerceIn(0, height - 1)
        if (labels[cy * width + cx].toInt() != 0) return cy * width + cx

        var best = -1
        var bestDistance = Int.MAX_VALUE
        for (r in 1..NEAREST_SEARCH_RADIUS) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (maxOf(abs(dx), abs(dy)) != r) continue
                    val px = cx + dx
                    val py = cy + dy
                    val distance = dx * dx + dy * dy
                    if (regionAt(px, py) != 0 && distance < bestDistance) {
                        best = py * width + px
                        bestDistance = distance
                    }
                }
            }
            // Pixels in later rings are at least r + 1 away.
            if (best >= 0 && bestDistance <= (r + 1) * (r + 1)) return best
        }
        return best
    }

    /** Sets the region's dilated mask pixels to 0xFF in a full-document alpha buffer. */
    internal fun writeMask(id: Int, dest: ByteArray, stride: Int) {
        val b = region(id).bounds
        val label = id.toShort()
        for (y in b.top until b.bottom) {
            val row = y * width
            val out = y * stride
            for (x in b.left until b.right) {
                if (labels[row + x] == label) dest[out + x] = OPAQUE
            }
        }
        for (i in halos[id - 1]) dest[(i / width) * stride + i % width] = OPAQUE
    }

    companion object {
        /** How far [regionNear] looks for a region when the point is on the outline. */
        const val NEAREST_SEARCH_RADIUS = 32

        private const val OPAQUE: Byte = -1

        /**
         * Labels [outline] (ALPHA_8). Masks are dilated by [outlineWidthPx] / 2 + 1 so paint tucks
         * under the anti-aliased line, but only over outline pixels, never into another region.
         * Blocking: call it off the main thread.
         */
        fun build(outline: Bitmap, outlineWidthPx: Float): RegionMap {
            require(outline.config == Bitmap.Config.ALPHA_8) { "Outline bitmap must be ALPHA_8" }
            val stride = outline.rowBytes
            val alpha = ByteArray(stride * outline.height)
            outline.copyPixelsToBuffer(ByteBuffer.wrap(alpha))
            return build(alpha, outline.width, outline.height, stride, outlineWidthPx / 2f + 1f)
        }

        internal fun build(alpha: ByteArray, width: Int, height: Int, stride: Int, dilationPx: Float): RegionMap {
            val labels = RegionLabeler.label(alpha, width, height, stride, dilationPx)
            val regions = labels.regions.map {
                Region(it.id, it.bounds.toRect(), it.maskBounds.toRect(), it.area, it.isBackground)
            }
            return RegionMap(width, height, labels.labels, regions, labels.halos)
        }

        private fun Box.toRect() = Rect(left, top, right, bottom)
    }
}
