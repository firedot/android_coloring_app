package com.fireworm.colourfun.drawing.regions

import kotlin.math.floor

/** A pixel rectangle: [left], [top] inclusive, [right], [bottom] exclusive. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * One closed area of an outline.
 *
 * @param bounds bounding box of the region's own pixels.
 * @param maskBounds bounding box of its dilated mask (the region plus the outline under its edge).
 * @param area number of the region's own pixels.
 * @param isBackground the region touches the image border.
 */
class LabeledRegion(val id: Int, val bounds: Box, val maskBounds: Box, val area: Int, val isBackground: Boolean)

/**
 * The result of [RegionLabeler.label]: a region id per pixel (`0` = line), the regions (index
 * `id - 1`) and, per region, the line pixels within the dilation radius of it.
 */
class RegionLabels(
    val width: Int,
    val height: Int,
    val labels: ShortArray,
    val regions: List<LabeledRegion>,
    val halos: Array<IntArray>,
)

/**
 * Splits an outline into closed regions: 4-connected, two-pass union-find over the non-line pixels.
 * With 4-connectivity a diagonal one-pixel gap in a line still separates two regions.
 *
 * Pure Kotlin (no `android.*`), so the JVM picture-asset test uses it too. Blocking.
 */
object RegionLabeler {
    /** A pixel with outline alpha above this counts as line. */
    const val LINE_ALPHA_THRESHOLD = 96

    private const val MAX_REGIONS_PER_HALO_PIXEL = 8

    /**
     * @param alpha outline alpha, row by row with [stride] bytes per row.
     * @param dilationPx how far each region's mask reaches into the line around it, so paint tucks
     * under the anti-aliased edge. The mask only grows over line pixels, never into another region.
     */
    fun label(alpha: ByteArray, width: Int, height: Int, stride: Int = width, dilationPx: Float = 0f): RegionLabels {
        val n = width * height
        val provisional = IntArray(n)
        val sets = UnionFind()

        // Pass 1: provisional labels, recording which ones touch.
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((alpha[y * stride + x].toInt() and 0xFF) > LINE_ALPHA_THRESHOLD) continue
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

        // Halos: line pixels within the dilation radius of each region.
        val maskMinX = minX.copyOf()
        val maskMinY = minY.copyOf()
        val maskMaxX = maxX.copyOf()
        val maskMaxY = maxY.copyOf()
        val halos = Array(count) { IntList() }
        val offsets = discOffsets(dilationPx)
        val nearby = IntArray(MAX_REGIONS_PER_HALO_PIXEL)
        if (offsets.isNotEmpty()) {
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
        }

        val regions = (1..count).map { id ->
            LabeledRegion(
                id = id,
                bounds = Box(minX[id], minY[id], maxX[id] + 1, maxY[id] + 1),
                maskBounds = Box(maskMinX[id], maskMinY[id], maskMaxX[id] + 1, maskMaxY[id] + 1),
                area = area[id],
                isBackground = border[id],
            )
        }
        return RegionLabels(width, height, labels, regions, Array(count) { halos[it].toArray() })
    }

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
