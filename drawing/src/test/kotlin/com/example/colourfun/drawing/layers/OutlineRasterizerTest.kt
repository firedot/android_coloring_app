package com.example.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Color
import com.example.colourfun.drawing.TestOutline
import com.example.colourfun.drawing.document.DocumentSize
import com.example.colourfun.generator.geometry.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutlineRasterizerTest {

    private val head = TestOutline.HEAD_CENTER
    private val body = TestOutline.BODY_CENTER
    private val leftEar = TestOutline.LEFT_EAR_CENTER
    private val rightEar = TestOutline.RIGHT_EAR_CENTER

    // Points on each shape's outline, in base document pixels.
    private val bodyTopUnderHead = Vec2(body.x, body.y - TestOutline.BODY_RY)
    private val bodyBottom = Vec2(body.x, body.y + TestOutline.BODY_RY)
    private val bodyLeft = Vec2(body.x - TestOutline.BODY_RX, body.y)
    private val headBottom = Vec2(head.x, head.y + TestOutline.HEAD_RADIUS)
    private val leftEarBottomUnderHead = Vec2(leftEar.x, leftEar.y + TestOutline.EAR_RY)
    private val leftEarTop = Vec2(leftEar.x, leftEar.y - TestOutline.EAR_RY)
    private val rightEarTop = Vec2(rightEar.x, rightEar.y - TestOutline.EAR_RY)

    @Test
    fun `produces an alpha-only bitmap of the document size`() {
        val outline = OutlineRasterizer.rasterize(TestOutline.lineArt, DocumentSize.Standard)

        assertEquals(Bitmap.Config.ALPHA_8, outline.config)
        assertEquals(DocumentSize.Standard.width, outline.width)
        assertEquals(DocumentSize.Standard.height, outline.height)
    }

    @Test
    fun `lines hidden behind the head are removed`() {
        val outline = OutlineRasterizer.rasterize(TestOutline.lineArt, DocumentSize.Standard)

        assertNoLine(outline, bodyTopUnderHead)
        assertNoLine(outline, leftEarBottomUnderHead)
    }

    @Test
    fun `visible lines remain`() {
        val outline = OutlineRasterizer.rasterize(TestOutline.lineArt, DocumentSize.Standard)

        assertLine(outline, bodyBottom)
        assertLine(outline, bodyLeft)
        assertLine(outline, headBottom)
        assertLine(outline, leftEarTop)
        assertLine(outline, rightEarTop)
    }

    @Test
    fun `shape interiors are empty`() {
        val outline = OutlineRasterizer.rasterize(TestOutline.lineArt, DocumentSize.Standard)

        assertNoLine(outline, head)
        assertNoLine(outline, Vec2(body.x, body.y + TestOutline.BODY_RY / 2))
    }

    @Test
    fun `scales line art to a low-RAM document`() {
        val size = DocumentSize.LowRam
        val outline = OutlineRasterizer.rasterize(TestOutline.lineArt, size)

        assertEquals(size.width, outline.width)
        assertLine(outline, bodyBottom, size.scale)
        assertLine(outline, headBottom, size.scale)
        assertNoLine(outline, bodyTopUnderHead, size.scale)
    }

    private fun alphaAt(outline: Bitmap, p: Vec2, scale: Float): Int =
        Color.alpha(outline.getPixel((p.x * scale).toInt(), (p.y * scale).toInt()))

    private fun assertLine(outline: Bitmap, p: Vec2, scale: Float = 1f) {
        val alpha = alphaAt(outline, p, scale)
        assertTrue("Expected a line at $p, alpha was $alpha", alpha > OutlineLayer.LINE_ALPHA_THRESHOLD)
    }

    private fun assertNoLine(outline: Bitmap, p: Vec2, scale: Float = 1f) {
        assertEquals("Expected no line at $p", 0, alphaAt(outline, p, scale))
    }
}
