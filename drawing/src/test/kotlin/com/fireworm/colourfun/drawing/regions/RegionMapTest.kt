package com.fireworm.colourfun.drawing.regions

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegionMapTest {

    @Test
    fun `a point on the outline picks the nearest region`() {
        val map = grid(
            "..###....",
            "..###....",
            "..###....",
        )
        val left = map.regionAt(0, 1)
        val right = map.regionAt(8, 1)

        // x = 2 is the line's left column: one pixel from the left region, three from the right.
        assertEquals(left, map.regionNear(2.5f, 1.5f))
        assertEquals(right, map.regionNear(4.5f, 1.5f))
    }

    @Test
    fun `masks cover the outline under the region's edge but not other regions`() {
        val map = grid(
            "....###....",
            "....###....",
            "....###....",
            dilationPx = 2f,
        )
        val left = map.regionAt(0, 1)
        val mask = ByteArray(11 * 3)
        map.writeMask(left, mask, stride = 11)

        val row = (0 until 11).map { mask[11 + it] != 0.toByte() }
        // Region pixels 0..3, outline 4..5 within 2 px of the region, outline 6 is not, nor the right region.
        assertEquals(listOf(true, true, true, true, true, true, false, false, false, false, false), row)
        assertEquals(Rect(0, 0, 6, 3), map.region(left).maskBounds)
    }

    /** '#' = opaque line, '*' = alpha 97 (line), '+' = alpha 96 (not a line), '.' = empty. */
    private fun grid(vararg rows: String, dilationPx: Float = 1.5f): RegionMap {
        val width = rows.first().length
        val alpha = ByteArray(width * rows.size)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                alpha[y * width + x] = when (c) {
                    '#' -> 255
                    '*' -> 97
                    '+' -> 96
                    else -> 0
                }.toByte()
            }
        }
        return RegionMap.build(alpha, width, rows.size, stride = width, dilationPx = dilationPx)
    }
}
