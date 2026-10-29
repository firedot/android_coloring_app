package com.fireworm.colourfun.drawing.regions

import android.graphics.Bitmap
import android.graphics.Color
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.TestOutline
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.drawing.brushes.StrokePoint
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.layers.OutlineLayer
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RegionFillTest {

    /** A quarter-scale 20:9 document: the 512 × 384 picture with blank margins on both sides. */
    private val size = DocumentSize(854, 384, scale = 0.25f)
    private val scale = size.scale
    private val outlineWidth = TestOutline.STROKE_WIDTH * scale
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private val outline = OutlineLayer.decode(TestOutline.png, size)
    private val regions = RegionMap.build(outline, outlineWidth)
    private val engine = DrawingEngine(
        Document(size, outline),
        RegionMaskCache(regions, workDispatcher = dispatcher),
        History(scope = scope, compressDispatcher = dispatcher),
    )

    private val headX = TestOutline.docX(size, TestOutline.HEAD_CENTER.x)
    private val headY = TestOutline.docY(size, TestOutline.HEAD_CENTER.y)
    private val head = regions.regionAt(headX.toInt(), headY.toInt())

    @Test
    fun `the test outline has separate background, body, head and ear regions`() {
        assertNotEquals(0, head)
        assertTrue(regions.region(regions.regionAt(2, 2)).isBackground)
        val body = regions.regionAt(headX.toInt(), TestOutline.docY(size, TestOutline.BODY_CENTER.y + 160).toInt())
        val leftEar = regions.regionAt(
            TestOutline.docX(size, TestOutline.LEFT_EAR_CENTER.x).toInt(),
            TestOutline.docY(size, TestOutline.LEFT_EAR_CENTER.y - TestOutline.EAR_RY / 2).toInt(),
        )
        assertEquals(4, setOf(regions.regionAt(2, 2), head, body, leftEar).size)
    }

    @Test
    fun `the side margins belong to the background`() {
        val background = regions.regionAt(2, 2)
        assertEquals(background, regions.regionAt(size.width - 3, size.height / 2))
        assertEquals(background, regions.regionAt(size.pictureLeft + 2, size.pictureTop + 2))
    }

    @Test
    fun `filling the background colours the side margins too`() {
        fill(2f, 2f, Color.RED)

        val paint = engine.document.paint.bitmap
        assertEquals(Color.RED, paint.getPixel(1, size.height / 2))
        assertEquals(Color.RED, paint.getPixel(size.width - 2, size.height / 2))
        assertNotEquals(Color.RED, paint.getPixel(headX.toInt(), headY.toInt()))
    }

    @Test
    fun `fill paints the whole region and nothing outside its dilated area`() {
        val blank = paintPixels()
        val outlineBefore = outlineBytes()

        fill(headX, headY, Color.RED)

        val filled = paintPixels()
        val allowed = dilatedRegion(head)
        for (i in filled.indices) {
            if (filled[i] != blank[i]) assertTrue("Pixel ${i % size.width},${i / size.width} outside the head changed", allowed[i])
            if (regions.regionAt(i % size.width, i / size.width) == head) assertEquals(Color.RED, filled[i])
        }
        assertArrayEquals("Outline bitmap changed", outlineBefore, outlineBytes())
    }

    @Test
    fun `fill replaces earlier paint in the region`() {
        fill(headX, headY, Color.RED)
        fill(headX, headY, Color.BLUE)

        assertEquals(Color.BLUE, engine.document.paint.bitmap.getPixel(headX.toInt(), headY.toInt()))
    }

    @Test
    fun `fill is undoable and redoable`() {
        val blank = paintPixels()
        fill(headX, headY, Color.RED)
        val filled = paintPixels()

        engine.undo()
        assertArrayEquals(blank, paintPixels())
        engine.redo()
        assertArrayEquals(filled, paintPixels())
    }

    @Test
    fun `a tap on the outline fills the nearest region`() {
        val onHeadLine = TestOutline.docY(size, TestOutline.HEAD_CENTER.y + TestOutline.HEAD_RADIUS) - 0.5f
        val expected = regions.regionNear(headX, onHeadLine)
        assertNotEquals(0, expected)

        fill(headX, onHeadLine, Color.RED)

        val probe = regions.region(expected).bounds
        val inside = (probe.top until probe.bottom).asSequence()
            .flatMap { y -> (probe.left until probe.right).asSequence().map { x -> x to y } }
            .first { (x, y) -> regions.regionAt(x, y) == expected }
        assertEquals(Color.RED, engine.document.paint.bitmap.getPixel(inside.first, inside.second))
    }

    @Test
    fun `staying inside the lines keeps a stroke in its starting region`() {
        val blank = paintPixels()
        val outlineBefore = outlineBytes()

        // From the head centre straight out through the outline into the background.
        drawStroke(headX, headY, headX + 200f, headY, stayInsideLines = true)

        val allowed = dilatedRegion(head)
        val painted = paintPixels()
        var changed = 0
        for (i in painted.indices) {
            if (painted[i] == blank[i]) continue
            changed++
            assertTrue("Pixel ${i % size.width},${i / size.width} outside the head changed", allowed[i])
        }
        assertTrue("The stroke drew nothing", changed > 0)
        assertArrayEquals("Outline bitmap changed", outlineBefore, outlineBytes())
    }

    @Test
    fun `without staying inside the lines the same stroke crosses the outline`() {
        val blank = paintPixels()

        drawStroke(headX, headY, headX + 200f, headY, stayInsideLines = false)

        val allowed = dilatedRegion(head)
        val painted = paintPixels()
        assertTrue(painted.indices.any { painted[it] != blank[it] && !allowed[it] })
    }

    private fun fill(x: Float, y: Float, color: Int) {
        scope.launch { engine.fill(x, y, color) }
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun drawStroke(fromX: Float, fromY: Float, toX: Float, toY: Float, stayInsideLines: Boolean) {
        val spec = BrushSpec(Medium.Pen, BrushSize.Large)
        engine.beginStroke(spec, Color.RED, StrokePoint(fromX, fromY, 0), stayInsideLines)
        engine.extendStroke(List(20) { i ->
            val t = (i + 1) / 20f
            StrokePoint(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, (i + 1) * 8L)
        })
        engine.endStroke()
    }

    /**
     * Reference: every pixel within the dilation radius of the region, by brute force. The engine's
     * masks are this, minus other regions' pixels, so they must stay inside it.
     */
    private fun dilatedRegion(id: Int): BooleanArray {
        val radius = outlineWidth / 2f + 1f
        val r = floor(radius).toInt()
        return BooleanArray(size.width * size.height) { i ->
            val x = i % size.width
            val y = i / size.width
            (-r..r).any { dy -> (-r..r).any { dx -> dx * dx + dy * dy <= radius * radius && regions.regionAt(x + dx, y + dy) == id } }
        }
    }

    private fun paintPixels(): IntArray {
        val bitmap = engine.document.paint.bitmap
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }

    private fun outlineBytes(): ByteArray {
        val bitmap: Bitmap = engine.document.outline.bitmap
        return ByteArray(bitmap.byteCount).also { bitmap.copyPixelsToBuffer(ByteBuffer.wrap(it)) }
    }
}
