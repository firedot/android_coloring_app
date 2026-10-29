package com.fireworm.colourfun.ui.palette

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A place on the two palette strips, both `0..1`.
 *
 * @param hue along the colour strip: the rainbow from red to pink, then a grey block at the end.
 * @param shade along the shade strip: white, the full colour in the middle, then black.
 */
data class SpectrumPosition(val hue: Float, val shade: Float)

/**
 * Maps palette strip positions to colours and back. Light and dark shades of the rainbow cover
 * the colours a picture needs without separate swatches: brown is dark orange, pink is light red,
 * navy is dark blue. Pure Kotlin so it is testable without Android.
 */
object Spectrum {
    /** Share of the colour strip taken by the rainbow; the rest is the grey block. */
    const val RAINBOW_END = 0.88f

    /** The rainbow stops at pink so the strip doesn't wrap back to red. */
    private const val LAST_HUE = 330f
    private const val SATURATION = 0.85f

    /** Colours greyer than this (pipette samples) are placed on the grey block. */
    private const val GREY_SATURATION_LIMIT = 0.25f
    private const val GREY_POSITION = (RAINBOW_END + 1f) / 2f

    val Default = SpectrumPosition(hue = 210f / LAST_HUE * RAINBOW_END, shade = 0.5f)

    /** Hue in degrees at a colour-strip position, or `null` on the grey block. */
    fun hueAt(position: Float): Float? = if (position > RAINBOW_END) null else position / RAINBOW_END * LAST_HUE

    /** Opaque ARGB colour at a place on the strips. */
    fun color(position: SpectrumPosition): Int {
        val lightness = 1f - position.shade
        val hue = hueAt(position.hue) ?: return hsl(0f, 0f, lightness)
        return hsl(hue, SATURATION, lightness)
    }

    /** The place on the strips nearest to [color], for a colour that came from the pipette. */
    fun position(color: Int): SpectrumPosition {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val lightness = (max + min) / 2f
        val chroma = max - min
        val saturation = if (chroma == 0f) 0f else chroma / (1f - abs(2f * lightness - 1f))
        val shade = 1f - lightness
        if (saturation < GREY_SATURATION_LIMIT) return SpectrumPosition(GREY_POSITION, shade)

        val hue = when (max) {
            r -> 60f * ((((g - b) / chroma) % 6f + 6f) % 6f)
            g -> 60f * ((b - r) / chroma + 2f)
            else -> 60f * ((r - g) / chroma + 4f)
        }
        // Reds between pink and the wrap-around go to whichever end of the rainbow is nearer.
        val onStrip = when {
            hue <= LAST_HUE -> hue
            hue > (LAST_HUE + 360f) / 2f -> 0f
            else -> LAST_HUE
        }
        return SpectrumPosition(onStrip / LAST_HUE * RAINBOW_END, shade)
    }

    private fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val sector = hue / 60f
        val x = chroma * (1f - abs(sector % 2f - 1f))
        val (r, g, b) = when (sector.toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        val m = lightness - chroma / 2f
        fun channel(value: Float) = ((value + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}

val DefaultColor: Int = Spectrum.color(Spectrum.Default)
