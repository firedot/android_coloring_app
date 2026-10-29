package com.fireworm.colourfun.ui.theme

import androidx.annotation.StringRes
import com.fireworm.colourfun.R

/** A palette colour: opaque ARGB plus its spoken name (for accessibility; children see no text). */
class PaletteColor(val argb: Int, @StringRes val name: Int)

/**
 * Every colour a child can choose; the only place colours are defined. A 12-colour wheel (primary,
 * secondary and tertiary colours) plus neutrals.
 */
object Palette {
    val Red = PaletteColor(0xFFE53935.toInt(), R.string.color_red)
    val Yellow = PaletteColor(0xFFFDD835.toInt(), R.string.color_yellow)
    val Blue = PaletteColor(0xFF1E63D6.toInt(), R.string.color_blue)

    val Orange = PaletteColor(0xFFFB8C00.toInt(), R.string.color_orange)
    val Green = PaletteColor(0xFF43A047.toInt(), R.string.color_green)
    val Violet = PaletteColor(0xFF8E24AA.toInt(), R.string.color_violet)

    val RedOrange = PaletteColor(0xFFF4511E.toInt(), R.string.color_red_orange)
    val YellowOrange = PaletteColor(0xFFFFB300.toInt(), R.string.color_yellow_orange)
    val YellowGreen = PaletteColor(0xFF9CCC3C.toInt(), R.string.color_yellow_green)
    val BlueGreen = PaletteColor(0xFF00897B.toInt(), R.string.color_blue_green)
    val BlueViolet = PaletteColor(0xFF5E35B1.toInt(), R.string.color_blue_violet)
    val RedViolet = PaletteColor(0xFFC2185B.toInt(), R.string.color_red_violet)

    val Black = PaletteColor(0xFF212121.toInt(), R.string.color_black)
    val White = PaletteColor(0xFFFFFFFF.toInt(), R.string.color_white)
    val Grey = PaletteColor(0xFF9E9E9E.toInt(), R.string.color_grey)
    val Brown = PaletteColor(0xFF8D5524.toInt(), R.string.color_brown)
    val Pink = PaletteColor(0xFFF48FB1.toInt(), R.string.color_pink)

    /** The colour wheel in wheel order, starting at red. */
    val Wheel: List<PaletteColor> = listOf(
        Red, RedOrange, Orange, YellowOrange, Yellow, YellowGreen,
        Green, BlueGreen, Blue, BlueViolet, Violet, RedViolet,
    )

    val Neutrals: List<PaletteColor> = listOf(Black, White, Grey, Brown, Pink)

    /** The colour a child starts with. */
    val Default: PaletteColor = Blue
}
