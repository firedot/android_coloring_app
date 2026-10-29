package com.fireworm.colourfun.drawing.viewport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM: [Viewport] has no Android dependencies. */
class ViewportTest {

    @Test
    fun `fit shows the whole document on a matching screen`() {
        // A 20:9 phone with its 3413 × 1536 document.
        val viewport = Viewport.of(3413, 1536, 2400f, 1080f)

        assertEquals(1f, viewport.scale)
        assertEquals(1080f / 1536f, viewport.zoom, 1e-4f)
        assertEquals(0f, viewport.toScreenY(0f), 0.5f)
        assertEquals(2400f, viewport.toScreenX(3413f), 1f)
    }

    @Test
    fun `fit letterboxes a document of another aspect and centres it`() {
        // A 4:3 document reopened on a 20:9 screen: bars left and right.
        val pillar = Viewport.of(2048, 1536, 2400f, 1080f)
        assertEquals(1080f / 1536f, pillar.zoom, 1e-4f)
        assertEquals((2400f - 2048f * pillar.zoom) / 2f, pillar.offsetX, 0.01f)
        assertEquals(0f, pillar.offsetY, 0.01f)

        // A 21:9-wide document on a 4:3 screen: bars top and bottom.
        val letter = Viewport.of(3456, 1536, 2048f, 1536f)
        assertEquals(2048f / 3456f, letter.zoom, 1e-4f)
        assertEquals(0f, letter.offsetX, 0.01f)
        assertEquals((1536f - 1536f * letter.zoom) / 2f, letter.offsetY, 0.01f)
    }

    @Test
    fun `zoom keeps the document point under the focus`() {
        val viewport = Viewport.of(2048, 1536, 1024f, 768f)
        val docX = viewport.toDocumentX(300f)
        val docY = viewport.toDocumentY(200f)

        val zoomed = viewport.transform(2f, focusX = 300f, focusY = 200f)

        assertEquals(2f, zoomed.scale)
        assertEquals(300f, zoomed.toScreenX(docX), 0.01f)
        assertEquals(200f, zoomed.toScreenY(docY), 0.01f)
    }

    @Test
    fun `zoom stays between fit and 4x`() {
        val viewport = Viewport.of(2048, 1536, 1024f, 768f)

        assertEquals(4f, viewport.transform(10f, 512f, 384f).scale)
        assertEquals(1f, viewport.transform(2f, 512f, 384f).transform(0.1f, 512f, 384f).scale)
    }

    @Test
    fun `panning is clamped so the document always covers the screen`() {
        val zoomed = Viewport.of(2048, 1536, 1024f, 768f).transform(2f, 512f, 384f)

        val farLeft = zoomed.pan(-10_000f, -10_000f)
        assertEquals(1024f, farLeft.toScreenX(2048f), 0.01f)
        assertEquals(768f, farLeft.toScreenY(1536f), 0.01f)

        val farRight = zoomed.pan(10_000f, 10_000f)
        assertEquals(0f, farRight.toScreenX(0f), 0.01f)
        assertEquals(0f, farRight.toScreenY(0f), 0.01f)
    }

    @Test
    fun `an axis where the document is smaller than the view stays centred`() {
        // 4:3 document on a 20:9 screen, zoomed a little: still narrower than the screen.
        val viewport = Viewport.of(2048, 1536, 2400f, 1080f).transform(1.2f, 1200f, 540f)
        val contentWidth = 2048f * viewport.zoom
        assertTrue(contentWidth < 2400f)

        val panned = viewport.pan(500f, 0f)

        assertEquals((2400f - contentWidth) / 2f, panned.offsetX, 0.01f)
    }

    @Test
    fun `fit returns to the whole document`() {
        val viewport = Viewport.of(2048, 1536, 1024f, 768f)
        val moved = viewport.transform(3f, 100f, 100f).pan(-200f, -50f)

        assertTrue(moved.isZoomedIn)
        assertEquals(viewport, moved.fitted())
        assertFalse(moved.fitted().isZoomedIn)
    }

    @Test
    fun `zoomed in only above 1_05`() {
        val viewport = Viewport.of(2048, 1536, 1024f, 768f)

        assertFalse(viewport.transform(1.05f, 0f, 0f).isZoomedIn)
        assertTrue(viewport.transform(1.06f, 0f, 0f).isZoomedIn)
    }

    @Test
    fun `document and screen coordinates round trip`() {
        val viewport = Viewport.of(3413, 1536, 2400f, 1080f).transform(2.7f, 700f, 300f).pan(-123f, 45f)

        for ((x, y) in listOf(0f to 0f, 1000f to 500f, 3413f to 1536f, 1706.5f to 768f)) {
            assertEquals(x, viewport.toDocumentX(viewport.toScreenX(x)), 0.01f)
            assertEquals(y, viewport.toDocumentY(viewport.toScreenY(y)), 0.01f)
        }
    }

    @Test
    fun `interpolating to fit ends exactly at fit`() {
        val viewport = Viewport.of(2048, 1536, 1024f, 768f)
        val zoomed = viewport.transform(3f, 100f, 100f)

        val halfway = zoomed.lerp(zoomed.fitted(), 0.5f)
        assertEquals(2f, halfway.scale, 0.01f)
        assertEquals(viewport, zoomed.lerp(zoomed.fitted(), 1f))
    }
}
