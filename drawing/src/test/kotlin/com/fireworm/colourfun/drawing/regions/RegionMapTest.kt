package com.fireworm.colourfun.drawing.regions

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegionMapTest {

    @Test
    fun `a diagonal line of one-pixel steps separates the two sides`() {
        val map = grid(
            "#.......",
            ".#......",
            "..#.....",
            "...#....",
            "....#...",
            ".....#..",
            "......#.",
            ".......#",
        )

        assertEquals(2, map.regions.size)
        assertNotEquals(map.regionAt(7, 0), map.regionAt(0, 7))
        // The two pixels touching only at a corner across the line stay apart.
        assertNotEquals(map.regionAt(1, 0), map.regionAt(0, 1))
    }

    @Test
    fun `nested shapes give separate regions with correct bounds and area`() {
        val map = grid(
            ".........",
            ".#######.",
            ".#.....#.",
            ".#.###.#.",
            ".#.#.#.#.",
            ".#.###.#.",
            ".#.....#.",
            ".#######.",
            ".........",
        )

        assertEquals(3, map.regions.size)
        val outside = map.region(map.regionAt(0, 0))
        val ring = map.region(map.regionAt(2, 2))
        val centre = map.region(map.regionAt(4, 4))

        assertEquals(setOf(outside.id, ring.id, centre.id).size, 3)
        assertEquals(Rect(2, 2, 7, 7), ring.bounds)
        assertEquals(16, ring.area)
        assertEquals(Rect(4, 4, 5, 5), centre.bounds)
        assertEquals(1, centre.area)
    }

    @Test
    fun `only regions touching the border are background`() {
        val map = grid(
            ".........",
            ".#######.",
            ".#.....#.",
            ".#.###.#.",
            ".#.#.#.#.",
            ".#.###.#.",
            ".#.....#.",
            ".#######.",
            ".........",
        )

        assertTrue(map.region(map.regionAt(0, 0)).isBackground)
        assertFalse(map.region(map.regionAt(2, 2)).isBackground)
        assertFalse(map.region(map.regionAt(4, 4)).isBackground)
    }

    @Test
    fun `outline pixels are region 0`() {
        val map = grid(
            "...",
            "###",
            "...",
        )

        assertEquals(0, map.regionAt(1, 1))
        assertNotEquals(map.regionAt(1, 0), map.regionAt(1, 2))
    }

    @Test
    fun `alpha above 96 is a line, 96 is not`() {
        val notALine = grid(
            "..+..",
            "..+..",
            "..+..",
        )
        val line = grid(
            "..*..",
            "..*..",
            "..*..",
        )

        assertEquals(1, notALine.regions.size)
        assertEquals(2, line.regions.size)
    }

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
