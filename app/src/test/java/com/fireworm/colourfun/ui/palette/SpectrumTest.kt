package com.fireworm.colourfun.ui.palette

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumTest {

    private val hues = listOf(0f, 0.2f, 0.5f, Spectrum.RAINBOW_END, 0.95f)

    @Test
    fun `the shade strip goes from white to black for every colour`() {
        hues.forEach { hue ->
            assertEquals(WHITE, color(hue, 0f))
            assertEquals(BLACK, color(hue, 1f))
        }
    }

    @Test
    fun `the middle of the shade strip is the full colour`() {
        val red = Rgb(color(0f, 0.5f))
        assertTrue("$red", red.r > 200 && red.g < 40 && red.b < 40)

        val blue = Rgb(DefaultColor)
        assertTrue("$blue", blue.b > 200 && blue.r < 40)
    }

    @Test
    fun `the rainbow runs from red to pink without wrapping back to red`() {
        val pink = Rgb(color(Spectrum.RAINBOW_END, 0.5f))

        assertTrue("$pink", pink.r > 200 && pink.g < 40 && pink.b in 100..160)
    }

    @Test
    fun `the grey block has no colour`() {
        listOf(0.2f, 0.5f, 0.8f).forEach { shade ->
            val grey = Rgb(color(0.95f, shade))
            assertTrue("$grey", grey.r == grey.g && grey.g == grey.b)
        }
    }

    @Test
    fun `brown and pink are shades of the rainbow`() {
        val brown = Rgb(color(hueOf(30f), 0.7f))
        assertTrue("$brown", brown.r > brown.g && brown.g > brown.b && brown.r < 170)

        val pink = Rgb(color(0f, 0.2f))
        assertTrue("$pink", pink.r > 230 && pink.g in 130..190 && pink.g == pink.b)
    }

    @Test
    fun `a colour from the strips finds its way back to the same place`() {
        for (hueStep in 0..20) for (shadeStep in 1..9) {
            val place = SpectrumPosition(hueStep / 20f * Spectrum.RAINBOW_END, shadeStep / 10f)

            val found = Spectrum.position(Spectrum.color(place))

            assertTrue("$place -> $found", abs(found.hue - place.hue) < 0.01f && abs(found.shade - place.shade) < 0.01f)
        }
    }

    @Test
    fun `colours from outside the strips land on the nearest place`() {
        assertTrue(Spectrum.position(WHITE).let { it.hue > Spectrum.RAINBOW_END && it.shade == 0f })
        assertTrue(Spectrum.position(0xFF808080.toInt()).hue > Spectrum.RAINBOW_END)
        // Reds past pink go to the nearer end of the rainbow.
        assertEquals(0f, Spectrum.position(0xFFFF0020.toInt()).hue)
        assertEquals(Spectrum.RAINBOW_END, Spectrum.position(0xFFFF0066.toInt()).hue)
    }

    @Test
    fun `every colour is opaque`() {
        for (hueStep in 0..20) for (shadeStep in 0..10) {
            assertEquals(0xFF, color(hueStep / 20f, shadeStep / 10f) ushr 24)
        }
    }

    private fun color(hue: Float, shade: Float) = Spectrum.color(SpectrumPosition(hue, shade))

    private fun hueOf(degrees: Float) = degrees / 330f * Spectrum.RAINBOW_END

    private data class Rgb(val r: Int, val g: Int, val b: Int) {
        constructor(argb: Int) : this(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
