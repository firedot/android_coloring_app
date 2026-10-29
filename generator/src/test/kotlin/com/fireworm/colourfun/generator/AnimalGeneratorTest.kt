package com.fireworm.colourfun.generator

import com.fireworm.colourfun.generator.parts.PartLibrary
import com.fireworm.colourfun.generator.random.Pcg32
import com.fireworm.colourfun.generator.template.QuadrupedTemplate
import com.fireworm.colourfun.generator.validation.LineArtValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalGeneratorTest {

    private val generator = AnimalGenerator(library)
    private val validator = LineArtValidator()

    @Test
    fun `every part file parses and every slot of the template has parts`() {
        QuadrupedTemplate.slots.forEach { slot ->
            assertTrue("No parts for ${slot.category}", library.candidates(slot.category, QuadrupedTemplate.id).isNotEmpty())
        }
    }

    @Test
    fun `10 000 seeds give valid, closed, deterministic pictures quickly`() {
        var fallbacks = 0
        var slowest = 0L
        val started = System.nanoTime()
        for (seed in 0L until SEEDS) {
            val t0 = System.nanoTime()
            val (art, attempt) = generator.generateWithAttempt(seed)
            slowest = maxOf(slowest, System.nanoTime() - t0)

            assertEquals(seed, art.seed)
            assertEquals(AnimalGenerator.GENERATOR_VERSION, art.generatorVersion)
            assertTrue("Seed $seed: stroke ${art.strokeWidth}", art.strokeWidth >= 10f)
            art.shapes.forEach { assertTrue("Seed $seed has an open path", it.path.isClosed) }
            assertNull("Seed $seed", validator.problem(art))
            if (seed % 50 == 0L) {
                assertEquals("Seed $seed is not deterministic", LineArtCodec.encode(art), LineArtCodec.encode(generator.generate(seed)))
            }
            if (attempt == null) fallbacks++
        }
        val averageMs = (System.nanoTime() - started) / 1e6 / SEEDS
        println("Average ${"%.2f".format(averageMs)} ms, slowest ${slowest / 1_000_000} ms, $fallbacks fallbacks")
        assertTrue("Average $averageMs ms per picture", averageMs < 50)
        assertTrue("Too many fallbacks: $fallbacks of $SEEDS", fallbacks < SEEDS / 100)
    }

    @Test
    fun `the fallback seed is valid on its first attempt`() {
        val art = generator.compose(AnimalGenerator.FALLBACK_SEED, Pcg32(AnimalGenerator.FALLBACK_SEED, stream = 0))
        assertNull(validator.problem(checkNotNull(art)))
    }

    @Test
    fun `the head stays within the template's head to body ratio`() {
        for (seed in 0L until 200) {
            val art = generator.compose(seed, Pcg32(seed, stream = 0)) ?: continue
            val widths = art.shapes.map { it.path.bounds().width }
            val body = widths[BODY_INDEX]
            val head = widths[HEAD_INDEX]
            assertTrue("Seed $seed: head/body ${head / body}", head / body in 0.449f..0.701f)
        }
    }

    @Test
    fun `golden pictures for fixed seeds`() {
        for (seed in GOLDEN_SEEDS) {
            val file = File("src/test/resources/golden/quadruped_$seed.json")
            val actual = LineArtCodec.encode(generator.generate(seed))
            if (System.getProperty("recordGoldens") == "true") {
                file.writeText(actual)
            } else {
                assertTrue("Missing ${file.path}; record with -PrecordGoldens=true", file.exists())
                assertEquals(
                    "Seed $seed changed: bump GENERATOR_VERSION and re-record with -PrecordGoldens=true",
                    file.readText(),
                    actual,
                )
            }
        }
    }

    @Test
    fun `line art survives a JSON round trip`() {
        val art = generator.generate(7)
        val decoded = LineArtCodec.decode(LineArtCodec.encode(art))
        assertEquals(LineArtCodec.encode(art), LineArtCodec.encode(decoded))
        assertEquals(art.shapes.size, decoded.shapes.size)
    }

    private companion object {
        const val SEEDS = 10_000L
        val GOLDEN_SEEDS = listOf(1L, 42L, 2026L)

        // Back to front with the starter parts: tail (1 path), body, 2 legs, 2 ears (2 paths each), head.
        const val BODY_INDEX = 1
        const val HEAD_INDEX = 8

        val library = PartLibrary.parse(
            File("src/main/assets/parts").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { it.readText() },
        )
    }
}
