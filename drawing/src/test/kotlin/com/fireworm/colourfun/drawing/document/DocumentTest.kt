package com.fireworm.colourfun.drawing.document

import android.graphics.Color
import com.fireworm.colourfun.drawing.TestOutline
import com.fireworm.colourfun.drawing.layers.OutlineLayer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentTest {

    /** A 20:9 screen at quarter scale: 853 × 384, picture 512 × 384 at x = 170. */
    private val size = DocumentSize(width = 853, height = 384, scale = 0.25f)
    private val document = Document(size, OutlineLayer.decode(TestOutline.png, size))

    @Test
    fun `the flattened picture is the picture rectangle only`() {
        val image = document.flattenPicture()

        assertEquals(size.pictureWidth, image.width)
        assertEquals(size.pictureHeight, image.height)
        assertEquals(4f / 3f, image.width.toFloat() / image.height, 0.01f)
    }

    @Test
    fun `it lines up with the document and leaves out the side margins`() {
        // Paint the left margin red and one spot inside the picture blue.
        document.paint.bitmap.apply {
            for (y in 0 until size.height) for (x in 0 until size.pictureLeft) setPixel(x, y, Color.RED)
            setPixel(size.pictureLeft + 10, size.pictureTop + 20, Color.BLUE)
        }

        val image = document.flattenPicture()

        assertEquals(Color.BLUE, image.getPixel(10, 20))
        val pixels = IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
        assertEquals(0, pixels.count { it == Color.RED })
    }

    @Test
    fun `the outline is drawn over the paint`() {
        val x = TestOutline.docX(size, TestOutline.HEAD_CENTER.x).toInt()
        val y = TestOutline.docY(size, TestOutline.HEAD_CENTER.y - TestOutline.HEAD_RADIUS).toInt()
        document.paint.bitmap.eraseColor(Color.YELLOW)

        val pixel = document.flattenPicture().getPixel(x - size.pictureLeft, y - size.pictureTop)

        assertEquals(0, Color.blue(pixel))
        assertEquals("line, not yellow paint", true, Color.red(pixel) < 128)
    }
}
