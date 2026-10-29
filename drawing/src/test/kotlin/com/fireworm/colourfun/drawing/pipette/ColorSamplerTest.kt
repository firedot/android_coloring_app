package com.fireworm.colourfun.drawing.pipette

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.regions.RegionMap
import java.nio.ByteBuffer
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorSamplerTest {

    private val width = 40
    private val height = 20

    // A vertical outline at x = 18..21 splits the picture into a left and a right region.
    private val lineLeft = 18
    private val lineRight = 21
    private val outline = createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
        val bytes = ByteArray(rowBytes * height)
        for (y in 0 until height) for (x in lineLeft..lineRight) bytes[y * rowBytes + x] = -1
        copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
    }
    private val regions = RegionMap.build(outline, outlineWidthPx = 4f)
    private val paint = createBitmap(width, height)

    private fun sampler(paper: Int = Color.WHITE) = ColorSampler(paint, paper, regions)

    @Test
    fun `unpainted paper gives the paper colour`() {
        assertEquals(Color.WHITE, sampler().sample(5f, 5f))
        assertEquals(PAPER_CREAM, sampler(PAPER_CREAM).sample(5f, 5f))
    }

    @Test
    fun `opaque paint is returned as is`() {
        paint.setPixel(5, 5, Color.RED)

        assertEquals(Color.RED, sampler().sample(5.5f, 5.5f))
    }

    @Test
    fun `semi-transparent paint is composited over the paper`() {
        paint.setPixel(5, 5, Color.argb(128, 255, 0, 0))

        val picked = sampler().sample(5f, 5f)

        // 50 % red over white is opaque pink: red stays 255, green and blue are about halfway.
        assertEquals(255, Color.alpha(picked))
        assertEquals(255, Color.red(picked))
        assertClose(127, Color.green(picked))
        assertClose(127, Color.blue(picked))
    }

    @Test
    fun `semi-transparent paint over a coloured paper blends with that paper`() {
        paint.setPixel(5, 5, Color.argb(128, 0, 0, 255))

        val picked = sampler(paper = Color.rgb(255, 255, 0)).sample(5f, 5f)

        assertEquals(255, Color.alpha(picked))
        assertClose(127, Color.red(picked))
        assertClose(127, Color.green(picked))
        assertClose(128, Color.blue(picked))
    }

    @Test
    fun `on the outline it samples the nearest non-outline pixel, not the paint under the line`() {
        fill(0 until lineLeft, Color.RED)
        fill(lineLeft..lineRight, Color.GREEN)
        fill(lineRight + 1 until width, Color.BLUE)

        // x = 18 is next to the left region, x = 21 next to the right one.
        assertEquals(Color.RED, sampler().sample(lineLeft + 0.5f, 10f))
        assertEquals(Color.BLUE, sampler().sample(lineRight + 0.5f, 10f))
    }

    @Test
    fun `on the outline next to unpainted paper it gives the paper colour, never black`() {
        val picked = sampler().sample(lineLeft + 0.5f, 10f)

        assertEquals(Color.WHITE, picked)
    }

    @Test
    fun `points outside the document sample the nearest edge`() {
        fill(0 until lineLeft, Color.RED)

        assertEquals(Color.RED, sampler().sample(-10f, 5f))
        assertEquals(Color.RED, sampler().sample(3f, 100f))
    }

    @Test
    fun `the picked colour is always opaque`() {
        paint.setPixel(5, 5, Color.argb(1, 10, 20, 30))
        paint.setPixel(6, 5, Color.argb(254, 10, 20, 30))

        assertTrue(listOf(sampler().sample(5f, 5f), sampler().sample(6f, 5f)).all { Color.alpha(it) == 255 })
    }

    private fun fill(columns: IntRange, color: Int) {
        for (y in 0 until height) for (x in columns) paint.setPixel(x, y, color)
    }

    /** Premultiplied storage rounds semi-transparent channels by a step. */
    private fun assertClose(expected: Int, actual: Int) {
        assertTrue("Expected about $expected, was $actual", abs(expected - actual) <= 2)
    }

    private companion object {
        val PAPER_CREAM = Color.rgb(255, 248, 225)
    }
}
