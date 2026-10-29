package com.fireworm.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.fireworm.colourfun.drawing.TestOutline
import com.fireworm.colourfun.drawing.document.DocumentSize
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutlineLayerTest {

    @Test
    fun `alpha is 255 minus luminance, placed at the picture area`() {
        // A tiny document: a 4 × 3 picture area centred in 8 × 3.
        val size = DocumentSize(width = 8, height = 3, scale = 4f / DocumentSize.PICTURE_WIDTH)
        val picture = createBitmap(size.pictureWidth, size.pictureHeight).apply {
            eraseColor(Color.WHITE)
            this[0, 0] = Color.BLACK
            this[1, 1] = Color.rgb(128, 128, 128)
            this[3, 2] = Color.BLACK
        }

        val alpha = alphaOf(OutlineLayer.fromPicture(picture, size))

        val left = size.pictureLeft
        assertEquals(2, left)
        assertEquals(255, alpha[0 * 8 + left])
        assertEquals(127, alpha[1 * 8 + left + 1])
        assertEquals(255, alpha[2 * 8 + left + 3])
        assertEquals(0, alpha[0 * 8 + left + 1])
        // The side margins stay empty.
        for (y in 0 until 3) {
            for (x in listOf(0, 1, 6, 7)) assertEquals("($x, $y)", 0, alpha[y * 8 + x])
        }
    }

    @Test
    fun `a library PNG decodes into a document-sized outline with lines only inside the picture`() {
        val size = DocumentSize(width = 3413, height = 1536)

        val outline = OutlineLayer.decode(TestOutline.png, size)

        assertEquals(Bitmap.Config.ALPHA_8, outline.config)
        assertEquals(3413, outline.width)
        assertEquals(1536, outline.height)
        val alpha = alphaOf(outline)
        val headTop = TestOutline.docY(size, TestOutline.HEAD_CENTER.y - TestOutline.HEAD_RADIUS).toInt()
        val headX = TestOutline.docX(size, TestOutline.HEAD_CENTER.x).toInt()
        assertTrue(alpha[headTop * outline.width + headX] > OutlineLayer.LINE_ALPHA_THRESHOLD)
        for (y in 0 until outline.height step 16) {
            for (x in 0 until size.pictureLeft) assertEquals(0, alpha[y * outline.width + x])
        }
    }

    @Test
    fun `on a low-RAM document the picture is scaled down`() {
        val size = DocumentSize(width = 2560, height = 1152, scale = 0.75f)

        val outline = OutlineLayer.decode(TestOutline.png, size)

        assertEquals(2560, outline.width)
        val alpha = alphaOf(outline)
        val headTop = TestOutline.docY(size, TestOutline.HEAD_CENTER.y - TestOutline.HEAD_RADIUS).toInt()
        val headX = TestOutline.docX(size, TestOutline.HEAD_CENTER.x).toInt()
        assertTrue(alpha[headTop * outline.width + headX] > OutlineLayer.LINE_ALPHA_THRESHOLD)
    }

    @Test
    fun `a picture-sized PNG decodes without scaling`() {
        val size = DocumentSize(width = 8, height = 3, scale = 4f / DocumentSize.PICTURE_WIDTH)
        val picture = createBitmap(4, 3).apply {
            eraseColor(Color.WHITE)
            this[2, 1] = Color.BLACK
        }
        val png = ByteArrayOutputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }

        val alpha = alphaOf(OutlineLayer.decode(png, size))

        assertEquals(255, alpha[1 * 8 + size.pictureLeft + 2])
    }

    /** Unpadded alpha values, row by row. */
    private fun alphaOf(outline: Bitmap): IntArray {
        val bytes = ByteArray(outline.rowBytes * outline.height)
        outline.copyPixelsToBuffer(ByteBuffer.wrap(bytes))
        return IntArray(outline.width * outline.height) { i ->
            bytes[(i / outline.width) * outline.rowBytes + i % outline.width].toInt() and 0xFF
        }
    }
}
