package com.fireworm.colourfun.drawing.regions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM: [RegionLabeler] has no Android dependencies. */
class RegionLabelerTest {

    @Test
    fun `a diagonal line of one-pixel steps separates the two sides`() {
        val labels = grid(
            "#.......",
            ".#......",
            "..#.....",
            "...#....",
            "....#...",
            ".....#..",
            "......#.",
            ".......#",
        )

        assertEquals(2, labels.regions.size)
        assertNotEquals(labels.at(7, 0), labels.at(0, 7))
        // The two pixels touching only at a corner across the line stay apart.
        assertNotEquals(labels.at(1, 0), labels.at(0, 1))
    }

    @Test
    fun `nested shapes give separate regions with correct bounds and area`() {
        val labels = grid(
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

        assertEquals(3, labels.regions.size)
        val ring = labels.region(labels.at(2, 2))
        val centre = labels.region(labels.at(4, 4))

        assertEquals(3, setOf(labels.at(0, 0), ring.id, centre.id).size)
        assertEquals(Box(2, 2, 7, 7), ring.bounds)
        assertEquals(16, ring.area)
        assertEquals(Box(4, 4, 5, 5), centre.bounds)
        assertEquals(1, centre.area)
    }

    @Test
    fun `only regions touching the border are background`() {
        val labels = grid(
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

        assertTrue(labels.region(labels.at(0, 0)).isBackground)
        assertFalse(labels.region(labels.at(2, 2)).isBackground)
        assertFalse(labels.region(labels.at(4, 4)).isBackground)
    }

    @Test
    fun `side margins around a centred picture join its background`() {
        // A 4:3 picture area (columns 3..10) centred in a wider document; nothing within its margin.
        val labels = grid(
            "..............",
            "......##......",
            ".....#..#.....",
            ".....#..#.....",
            "......##......",
            "..............",
        )

        assertEquals(2, labels.regions.size)
        val background = labels.at(0, 0)
        assertEquals(background, labels.at(13, 5))
        assertEquals(background, labels.at(3, 0))
        assertTrue(labels.region(background).isBackground)
        assertFalse(labels.region(labels.at(6, 2)).isBackground)
    }

    @Test
    fun `outline pixels are region 0`() {
        val labels = grid(
            "...",
            "###",
            "...",
        )

        assertEquals(0, labels.at(1, 1))
        assertNotEquals(labels.at(1, 0), labels.at(1, 2))
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
    fun `halos reach into the line by the dilation radius only`() {
        val labels = grid(
            "....###....",
            "....###....",
            "....###....",
            dilationPx = 2f,
        )
        val left = labels.region(labels.at(0, 1))

        assertEquals(Box(0, 0, 6, 3), left.maskBounds)
        assertEquals(setOf(4, 5), labels.halos[left.id - 1].map { it % 11 }.toSet())
    }

    @Test
    fun `rows can be padded`() {
        val width = 3
        val stride = 4
        val alpha = ByteArray(stride * 3)
        for (y in 0 until 3) alpha[y * stride + 1] = 255.toByte()
        // Padding bytes that look like lines must be ignored.
        for (y in 0 until 3) alpha[y * stride + 3] = 255.toByte()

        val labels = RegionLabeler.label(alpha, width, 3, stride)

        assertEquals(2, labels.regions.size)
    }

    private fun RegionLabels.at(x: Int, y: Int): Int = labels[y * width + x].toInt()

    private fun RegionLabels.region(id: Int): LabeledRegion = regions[id - 1]

    /** '#' = opaque line, '*' = alpha 97 (line), '+' = alpha 96 (not a line), '.' = empty. */
    private fun grid(vararg rows: String, dilationPx: Float = 0f): RegionLabels {
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
        return RegionLabeler.label(alpha, width, rows.size, dilationPx = dilationPx)
    }
}
