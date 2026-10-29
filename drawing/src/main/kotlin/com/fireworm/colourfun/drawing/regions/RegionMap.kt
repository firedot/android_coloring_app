package com.fireworm.colourfun.drawing.regions

import android.graphics.Bitmap
import android.graphics.Rect
import com.fireworm.colourfun.drawing.layers.OutlineLayer
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.floor

/**
 * The closed areas of a picture, labelled from the rendered outline bitmap (never from vector
 * geometry, so it matches exactly what the child sees).
 *
 * Every pixel has a region id; `0` means outline. Labelling is 4-connected, so a diagonal
 * one-pixel gap in a line still separates two regions. Built off the main thread; read-only after.
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
            val n = width * height
            val provisional = IntArray(n)
            val sets = UnionFind()

            // Pass 1: provisional labels, recording which ones touch.
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if ((alpha[y * stride + x].toInt() and 0xFF) > OutlineLayer.LINE_ALPHA_THRESHOLD) continue
                    val i = y * width + x
                    val left = if (x > 0) provisional[i - 1] else 0
                    val up = if (y > 0) provisional[i - width] else 0
                    provisional[i] = when {
                        left == 0 && up == 0 -> sets.add()
                        left == 0 -> up
                        up == 0 -> left
                        else -> left.also { sets.union(left, up) }
                    }
                }
            }

            // Pass 2: final ids in scan order, plus bounds, area and border contact.
            val labels = ShortArray(n)
            val idOfRoot = IntArray(sets.size)
            val upperBound = sets.size
            val minX = IntArray(upperBound) { Int.MAX_VALUE }
            val minY = IntArray(upperBound) { Int.MAX_VALUE }
            val maxX = IntArray(upperBound) { -1 }
            val maxY = IntArray(upperBound) { -1 }
            val area = IntArray(upperBound)
            val border = BooleanArray(upperBound)
            var count = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val p = provisional[i]
                    if (p == 0) continue
                    val root = sets.find(p)
                    var id = idOfRoot[root]
                    if (id == 0) {
                        id = ++count
                        check(count <= Short.MAX_VALUE) { "Too many regions in the outline" }
                        idOfRoot[root] = id
                    }
                    labels[i] = id.toShort()
                    if (x < minX[id]) minX[id] = x
                    if (x > maxX[id]) maxX[id] = x
                    if (y < minY[id]) minY[id] = y
                    if (y > maxY[id]) maxY[id] = y
                    area[id]++
                    if (x == 0 || y == 0 || x == width - 1 || y == height - 1) border[id] = true
                }
            }

            // Halos: outline pixels within the dilation radius of each region.
            val maskMinX = minX.copyOf()
            val maskMinY = minY.copyOf()
            val maskMaxX = maxX.copyOf()
            val maskMaxY = maxY.copyOf()
            val halos = Array(count) { IntList() }
            val offsets = discOffsets(dilationPx)
            val nearby = IntArray(MAX_REGIONS_PER_HALO_PIXEL)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    if (labels[i].toInt() != 0) continue
                    var found = 0
                    for (k in offsets.indices step 2) {
                        val nx = x + offsets[k]
                        val ny = y + offsets[k + 1]
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        val id = labels[ny * width + nx].toInt()
                        if (id == 0 || nearby.containsWithin(found, id)) continue
                        if (found < nearby.size) nearby[found++] = id
                    }
                    for (k in 0 until found) {
                        val id = nearby[k]
                        halos[id - 1].add(i)
                        if (x < maskMinX[id]) maskMinX[id] = x
                        if (x > maskMaxX[id]) maskMaxX[id] = x
                        if (y < maskMinY[id]) maskMinY[id] = y
                        if (y > maskMaxY[id]) maskMaxY[id] = y
                    }
                }
            }

            val regions = (1..count).map { id ->
                Region(
                    id = id,
                    bounds = Rect(minX[id], minY[id], maxX[id] + 1, maxY[id] + 1),
                    maskBounds = Rect(maskMinX[id], maskMinY[id], maskMaxX[id] + 1, maskMaxY[id] + 1),
                    area = area[id],
                    isBackground = border[id],
                )
            }
            return RegionMap(width, height, labels, regions, Array(count) { halos[it].toArray() })
        }

        private const val MAX_REGIONS_PER_HALO_PIXEL = 8

        private fun IntArray.containsWithin(count: Int, value: Int): Boolean {
            for (k in 0 until count) if (this[k] == value) return true
            return false
        }

        /** (dx, dy) pairs within [radius], excluding the centre. */
        private fun discOffsets(radius: Float): IntArray {
            val r = floor(radius).toInt()
            val out = IntList()
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if ((dx != 0 || dy != 0) && dx * dx + dy * dy <= radius * radius) {
                        out.add(dx)
                        out.add(dy)
                    }
                }
            }
            return out.toArray()
        }
    }
}

/** Union-find over provisional labels; label 0 is reserved for the outline. */
private class UnionFind {
    private var parent = IntArray(1024)
    var size = 1
        private set

    fun add(): Int {
        if (size == parent.size) parent = parent.copyOf(size * 2)
        parent[size] = size
        return size++
    }

    fun find(label: Int): Int {
        var x = label
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }

    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra < rb) parent[rb] = ra else if (rb < ra) parent[ra] = rb
    }
}

private class IntList {
    private var data = IntArray(16)
    private var size = 0

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(): IntArray = data.copyOf(size)
}
