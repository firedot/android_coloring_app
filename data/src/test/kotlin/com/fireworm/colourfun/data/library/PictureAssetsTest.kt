package com.fireworm.colourfun.data.library

import com.fireworm.colourfun.drawing.regions.RegionLabeler
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Re-checks every catalog entry against the measurable picture rules (PICTURE_GUIDELINES.md): the
 * files exist, 2048 × 1536 8-bit grayscale, no line near the edge, 8–80 regions of at least
 * 1500 px² (6000 for easy pictures), unique ids.
 */
class PictureAssetsTest {

    private val dir = File("src/main/assets/pictures")
    private val catalog = PictureCatalog.parse(File(dir, "catalog.json").readText())

    @Test
    fun `the catalog has pictures in every section`() {
        assertTrue(catalog.pictures.isNotEmpty())
        val sectionIds = catalog.sections.map { it.id }
        assertEquals(sectionIds.toSet().size, sectionIds.size)
        catalog.sections.forEach { section -> assertTrue("${section.id} is empty", catalog.pictures.any { it.section == section.id }) }
    }

    @Test
    fun `ids are unique and match their section and files`() {
        val ids = catalog.pictures.map { it.id }
        assertEquals("duplicate ids", ids.toSet().size, ids.size)
        val sections = catalog.sections.map { it.id }.toSet()
        catalog.pictures.forEach { p ->
            val slug = p.id.substringAfter('/')
            assertTrue("${p.id}: unknown section", p.section in sections)
            assertEquals("${p.id}: id", "${p.section}/$slug", p.id)
            assertEquals("${p.id}: file", "${p.section}/$slug.png", p.file)
            assertEquals("${p.id}: thumb", "${p.section}/${slug}_thumb.webp", p.thumb)
            assertTrue("${p.id}: missing thumbnail", File(dir, p.thumb).isFile)
        }
    }

    @Test
    fun `every picture follows the rules`() {
        catalog.pictures.forEach { p ->
            val image = checkNotNull(ImageIO.read(File(dir, p.file))) { "${p.id}: unreadable" }
            assertEquals("${p.id}: 8-bit grayscale without alpha", BufferedImage.TYPE_BYTE_GRAY, image.type)
            assertEquals("${p.id}: width", WIDTH, image.width)
            assertEquals("${p.id}: height", HEIGHT, image.height)

            val alpha = outlineAlpha(image)
            assertEdgeMargin(p, alpha)

            val regions = RegionLabeler.label(alpha, WIDTH, HEIGHT).regions
            assertTrue("${p.id}: ${regions.size} regions", regions.size in MIN_REGIONS..MAX_REGIONS)
            assertEquals("${p.id}: catalog region count", regions.size, p.regions)
            val minimum = if (p.difficulty == Difficulty.Easy) MIN_AREA_EASY else MIN_AREA
            regions.forEach { r -> assertTrue("${p.id}: region at ${r.bounds} = ${r.area} px²", r.area >= minimum) }
        }
    }

    private fun assertEdgeMargin(p: Picture, alpha: ByteArray) {
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val nearEdge = x < EDGE_MARGIN || y < EDGE_MARGIN || x >= WIDTH - EDGE_MARGIN || y >= HEIGHT - EDGE_MARGIN
                if (nearEdge && (alpha[y * WIDTH + x].toInt() and 0xFF) > RegionLabeler.LINE_ALPHA_THRESHOLD) {
                    throw AssertionError("${p.id}: line at ($x, $y), within $EDGE_MARGIN px of the edge")
                }
            }
        }
    }

    /** Outline alpha as the app builds it: 255 − luminance. */
    private fun outlineAlpha(image: BufferedImage): ByteArray {
        val gray = IntArray(WIDTH * HEIGHT)
        image.raster.getSamples(0, 0, WIDTH, HEIGHT, 0, gray)
        return ByteArray(gray.size) { (255 - gray[it]).toByte() }
    }

    private companion object {
        const val WIDTH = 2048
        const val HEIGHT = 1536
        const val EDGE_MARGIN = 64
        const val MIN_REGIONS = 8
        const val MAX_REGIONS = 80
        const val MIN_AREA = 1500
        const val MIN_AREA_EASY = 6000
    }
}
