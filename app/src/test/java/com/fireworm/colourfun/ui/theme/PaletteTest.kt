package com.fireworm.colourfun.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    @Test
    fun `the wheel has the 12 colours in wheel order`() {
        val expected = listOf(
            0xE53935, 0xF4511E, 0xFB8C00, 0xFFB300, 0xFDD835, 0x9CCC3C,
            0x43A047, 0x00897B, 0x1E63D6, 0x5E35B1, 0x8E24AA, 0xC2185B,
        )

        assertEquals(expected, Palette.Wheel.map { it.argb and 0xFFFFFF })
    }

    @Test
    fun `primaries, secondaries and tertiaries alternate around the wheel`() {
        val primaries = listOf(Palette.Red, Palette.Yellow, Palette.Blue)
        val secondaries = listOf(Palette.Orange, Palette.Green, Palette.Violet)

        // Primaries every fourth place, secondaries halfway between them, tertiaries in the gaps.
        assertEquals(primaries, listOf(0, 4, 8).map(Palette.Wheel::get))
        assertEquals(secondaries, listOf(2, 6, 10).map(Palette.Wheel::get))
    }

    @Test
    fun `the neutrals follow the wheel`() {
        assertEquals(
            listOf(0x212121, 0xFFFFFF, 0x9E9E9E, 0x8D5524, 0xF48FB1),
            Palette.Neutrals.map { it.argb and 0xFFFFFF },
        )
    }

    @Test
    fun `every colour is opaque and different`() {
        val all = Palette.Wheel + Palette.Neutrals

        assertTrue(all.all { it.argb ushr 24 == 0xFF })
        assertEquals(17, all.map { it.argb }.toSet().size)
        assertEquals(17, all.map { it.name }.toSet().size)
        assertTrue(Palette.Default in Palette.Wheel)
    }
}
