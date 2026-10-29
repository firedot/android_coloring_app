package com.fireworm.colourfun.data.library

import com.fireworm.colourfun.drawing.regions.RegionLabeler
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Re-checks every picture in the asset folders against the measurable picture rules
 * (PICTURE_GUIDELINES.md): only `<name>.png` + `<name>_thumb.webp` pairs, 2048 × 1536 8-bit
 * grayscale, no line near the edge, 8–80 regions of at least 1500 px².
 */
class PictureAssetsTest {

    private val dir = File("src/main/assets/pictures")
    private val library = PictureLibrary.scan { path -> File(dir, path).list().orEmpty().toList() }
    private val pictures = library.bySection().flatMap { it.second }

    @Test
    fun `every known section has pictures`() {
        val sections = library.bySection().map { it.first.id }
        listOf("animals", "plants", "various").forEach { assertTrue("$it is empty", it in sections) }
    }

    @Test
    fun `the folders hold only pictures and their thumbnails`() {
        dir.listFiles().orEmpty().forEach { assertTrue("${it.name}: only section folders belong here", it.isDirectory) }
        dir.listFiles().orEmpty().forEach { section ->
            val files = section.list().orEmpty().toSet()
            files.forEach { name ->
                val picture = name.removeSuffix("_thumb.webp").removeSuffix(".png")
                assertTrue("${section.name}/$name: expected <name>.png or <name>_thumb.webp", name == "$picture.png" || name == "${picture}_thumb.webp")
                assertTrue("${section.name}/$name: name must be lowercase letters, digits and _", NAME.matches(picture))
                assertTrue("${section.name}/$picture: missing PNG", "$picture.png" in files)
                assertTrue("${section.name}/$picture: missing thumbnail", "${picture}_thumb.webp" in files)
            }
        }
    }

    @Test
    fun `every picture follows the rules`() {
        assertTrue(pictures.isNotEmpty())
        pictures.forEach { p ->
            val image = checkNotNull(ImageIO.read(File(dir, p.file))) { "${p.id}: unreadable" }
            assertEquals("${p.id}: 8-bit grayscale without alpha", BufferedImage.TYPE_BYTE_GRAY, image.type)
            assertEquals("${p.id}: width", WIDTH, image.width)
            assertEquals("${p.id}: height", HEIGHT, image.height)

            val alpha = outlineAlpha(image)
            assertEdgeMargin(p, alpha)

            val regions = RegionLabeler.label(alpha, WIDTH, HEIGHT).regions
            assertTrue("${p.id}: ${regions.size} regions", regions.size in MIN_REGIONS..MAX_REGIONS)
            regions.forEach { r -> assertTrue("${p.id}: region at ${r.bounds} = ${r.area} px²", r.area >= MIN_AREA) }
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
        val NAME = Regex("[a-z][a-z0-9_]*")
    }
}
