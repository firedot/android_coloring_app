package com.fireworm.colourfun.generator.validation

import com.fireworm.colourfun.generator.LineArt
import com.fireworm.colourfun.generator.geometry.Bounds
import com.fireworm.colourfun.generator.geometry.Vec2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Checks that generated line art makes a good colouring page. It rasterizes the art like the app
 * does (back to front, each shape clears its interior then strokes its outline), at a quarter of
 * the resolution, and labels the regions between the lines.
 *
 * Rules: shapes stay inside the document with a margin; the animal covers 45–85 % of the document
 * (by its larger side); there are 8–80 regions and none is smaller than ~1500 px².
 */
class LineArtValidator(private val downscale: Int = 4) {

    /** `null` if the art is fine, otherwise why not. */
    fun problem(art: LineArt): String? {
        val bounds = art.shapes.map { it.path.bounds() }.reduce(Bounds::union)
        val margin = MARGIN + art.strokeWidth / 2f
        if (bounds.left < margin || bounds.top < margin || bounds.right > art.width - margin || bounds.bottom > art.height - margin) {
            return "outside the document margin: $bounds"
        }
        val coverage = max(bounds.width / art.width, bounds.height / art.height)
        if (coverage !in COVERAGE) return "covers ${"%.2f".format(coverage)} of the document"

        val regions = regionAreas(art)
        if (regions.size !in REGION_COUNT) return "${regions.size} regions"
        val smallest = regions.min() * downscale * downscale
        if (smallest < MIN_REGION_AREA) return "smallest region $smallest px²"
        return null
    }

    /** Area of every region (including the background) in raster pixels. */
    internal fun regionAreas(art: LineArt): List<Int> {
        val width = art.width / downscale
        val height = art.height / downscale
        val line = BooleanArray(width * height)
        val scale = 1f / downscale
        val radius = art.strokeWidth * scale / 2f

        for (shape in art.shapes) {
            val polygons = shape.path.flatten().map { polygon -> polygon.map { it * scale } }
            if (shape.occludes) fill(polygons, line, width, height, value = false)
            for (polygon in polygons) {
                for (i in polygon.indices) stroke(polygon[i], polygon[(i + 1) % polygon.size], radius, line, width, height)
            }
        }
        return label(line, width, height)
    }

    /** Non-zero winding fill, sampling pixel centres, like Android's default path fill. */
    private fun fill(polygons: List<List<Vec2>>, grid: BooleanArray, width: Int, height: Int, value: Boolean) {
        val top = max(0, floor(polygons.minOf { p -> p.minOf { it.y } }).toInt())
        val bottom = min(height - 1, ceil(polygons.maxOf { p -> p.maxOf { it.y } }).toInt())
        val crossings = ArrayList<Pair<Float, Int>>()
        for (y in top..bottom) {
            val sampleY = y + 0.5f
            crossings.clear()
            for (polygon in polygons) {
                for (i in polygon.indices) {
                    val a = polygon[i]
                    val b = polygon[(i + 1) % polygon.size]
                    if ((a.y <= sampleY) == (b.y <= sampleY)) continue
                    val x = a.x + (sampleY - a.y) / (b.y - a.y) * (b.x - a.x)
                    crossings += x to (if (b.y > a.y) 1 else -1)
                }
            }
            crossings.sortBy { it.first }
            var winding = 0
            for (i in 0 until crossings.size - 1) {
                winding += crossings[i].second
                if (winding == 0) continue
                val from = max(0, ceil(crossings[i].first - 0.5f).toInt())
                val to = min(width - 1, floor(crossings[i + 1].first - 0.5f).toInt())
                for (x in from..to) grid[y * width + x] = value
            }
        }
    }

    /** Marks pixels whose centre is within [radius] of the segment. */
    private fun stroke(a: Vec2, b: Vec2, radius: Float, grid: BooleanArray, width: Int, height: Int) {
        val left = max(0, floor(min(a.x, b.x) - radius).toInt())
        val right = min(width - 1, ceil(max(a.x, b.x) + radius).toInt())
        val top = max(0, floor(min(a.y, b.y) - radius).toInt())
        val bottom = min(height - 1, ceil(max(a.y, b.y) + radius).toInt())
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        val radiusSq = radius * radius
        for (y in top..bottom) for (x in left..right) {
            val px = x + 0.5f - a.x
            val py = y + 0.5f - a.y
            val t = if (lengthSq == 0f) 0f else ((px * dx + py * dy) / lengthSq).coerceIn(0f, 1f)
            val ex = px - t * dx
            val ey = py - t * dy
            if (ex * ex + ey * ey <= radiusSq) grid[y * width + x] = true
        }
    }

    /** 4-connected flood fill over non-line pixels; returns each region's area. */
    private fun label(line: BooleanArray, width: Int, height: Int): List<Int> {
        val seen = BooleanArray(line.size)
        val stack = IntArray(line.size)
        val areas = mutableListOf<Int>()
        for (start in line.indices) {
            if (line[start] || seen[start]) continue
            var size = 0
            var area = 0
            stack[size++] = start
            seen[start] = true
            while (size > 0) {
                val i = stack[--size]
                area++
                val x = i % width
                if (x > 0 && !line[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[size++] = i - 1 }
                if (x < width - 1 && !line[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[size++] = i + 1 }
                if (i >= width && !line[i - width] && !seen[i - width]) { seen[i - width] = true; stack[size++] = i - width }
                if (i < line.size - width && !line[i + width] && !seen[i + width]) { seen[i + width] = true; stack[size++] = i + width }
            }
            areas += area
        }
        return areas
    }

    private companion object {
        const val MARGIN = 32f
        val COVERAGE = 0.45f..0.85f
        val REGION_COUNT = 8..80
        const val MIN_REGION_AREA = 1500
    }
}
