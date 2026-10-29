package com.fireworm.colourfun.drawing.document

import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain JVM: [DocumentSizer] and [DocumentSize] have no Android dependencies. */
class DocumentSizerTest {

    @Test
    fun `a 4 by 3 tablet gets exactly the picture`() {
        val size = DocumentSizer.forScreen(2048, 1536, isLowRamDevice = false)

        assertEquals(DocumentSize(2048, 1536, 1f), size)
        assertEquals(0, size.pictureLeft)
        assertEquals(0, size.pictureTop)
    }

    @Test
    fun `the width follows the screen aspect`() {
        assertEquals(2458, DocumentSizer.forScreen(2560, 1600, isLowRamDevice = false).width) // 16:10
        assertEquals(3413, DocumentSizer.forScreen(2400, 1080, isLowRamDevice = false).width) // 20:9
        assertEquals(1536, DocumentSizer.forScreen(2400, 1080, isLowRamDevice = false).height)
    }

    @Test
    fun `the picture is centred horizontally`() {
        val size = DocumentSizer.forScreen(2400, 1080, isLowRamDevice = false)

        assertEquals(2048, size.pictureWidth)
        assertEquals(1536, size.pictureHeight)
        assertEquals((3413 - 2048) / 2, size.pictureLeft)
        assertEquals(0, size.pictureTop)
    }

    @Test
    fun `screens wider than 2_25 to 1 are capped and letterboxed`() {
        assertEquals(3456, DocumentSizer.forScreen(2520, 1080, isLowRamDevice = false).width) // 21:9
        assertEquals(3456, DocumentSizer.forScreen(4000, 1000, isLowRamDevice = false).width)
    }

    @Test
    fun `screens narrower than 4 to 3 get the picture width and are letterboxed`() {
        assertEquals(2048, DocumentSizer.forScreen(1280, 1024, isLowRamDevice = false).width) // 5:4
        assertEquals(2048, DocumentSizer.forScreen(1000, 1000, isLowRamDevice = false).width)
    }

    @Test
    fun `a screen held upright is sized as in landscape`() {
        assertEquals(
            DocumentSizer.forScreen(2400, 1080, isLowRamDevice = false),
            DocumentSizer.forScreen(1080, 2400, isLowRamDevice = false),
        )
    }

    @Test
    fun `low-RAM devices scale everything by 0_75`() {
        val size = DocumentSizer.forScreen(2400, 1080, isLowRamDevice = true)

        assertEquals(DocumentSize(2560, 1152, 0.75f), size)
        assertEquals(1536, size.pictureWidth)
        assertEquals(1152, size.pictureHeight)
        assertEquals((2560 - 1536) / 2, size.pictureLeft)
        assertEquals(1536, DocumentSizer.forScreen(2048, 1536, isLowRamDevice = true).width)
        assertEquals(2592, DocumentSizer.forScreen(2520, 1080, isLowRamDevice = true).width)
    }
}
