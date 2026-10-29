package com.fireworm.colourfun.generator.geometry

import com.fireworm.colourfun.generator.geometry.PathCommand.Close
import com.fireworm.colourfun.generator.geometry.PathCommand.CubicTo
import com.fireworm.colourfun.generator.geometry.PathCommand.LineTo
import com.fireworm.colourfun.generator.geometry.PathCommand.MoveTo
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PathDataParserTest {

    @Test
    fun `absolute and relative lines, H, V and implicit line-tos`() {
        val path = PathDataParser.parse("M10,20 L30,20 h10 v-5 H0 V0 m5,5 10,0 z")

        assertEquals(
            listOf(
                MoveTo(Vec2(10f, 20f)), LineTo(Vec2(30f, 20f)), LineTo(Vec2(40f, 20f)), LineTo(Vec2(40f, 15f)),
                LineTo(Vec2(0f, 15f)), LineTo(Vec2(0f, 0f)), MoveTo(Vec2(5f, 5f)), LineTo(Vec2(15f, 5f)), Close,
            ),
            path.commands,
        )
    }

    @Test
    fun `compact number formats`() {
        val path = PathDataParser.parse("M-.5.5L1e1-2.5E-1Z")

        assertEquals(listOf(MoveTo(Vec2(-0.5f, 0.5f)), LineTo(Vec2(10f, -0.25f)), Close), path.commands)
    }

    @Test
    fun `S reflects the previous control point and Q becomes an exact cubic`() {
        val s = PathDataParser.parse("M0,0 C0,10 10,10 10,0 S20,-10 20,0").commands
        assertEquals(CubicTo(Vec2(10f, -10f), Vec2(20f, -10f), Vec2(20f, 0f)), s[2])

        val q = PathDataParser.parse("M0,0 Q15,30 30,0").commands[1] as CubicTo
        assertEquals(Vec2(10f, 20f), q.c1)
        assertEquals(Vec2(20f, 20f), q.c2)
    }

    @Test
    fun `arcs become cubics that stay on the circle`() {
        val path = PathDataParser.parse("M-50,0 A50,50 0 1,0 50,0 A50,50 0 1,0 -50,0 Z")

        val points = path.flatten(segmentsPerCubic = 16).single()
        points.forEach { assertTrue("$it is off the circle", abs(hypot(it.x, it.y) - 50f) < 0.2f) }
        assertEquals(Vec2(50f, 0f), (path.commands[2] as CubicTo).p)
        assertTrue(path.isClosed)
    }

    @Test
    fun `arc flags may be written without separators`() {
        val path = PathDataParser.parse("M0,0 a10,10 0 0110,10")

        assertEquals(Vec2(10f, 10f), (path.commands.last() as CubicTo).p)
    }

    @Test
    fun `open sub-paths are detected`() {
        assertTrue(PathDataParser.parse("M0,0 L1,0 L1,1 Z M5,5 L6,5 Z").isClosed)
        assertFalse(PathDataParser.parse("M0,0 L1,0 L1,1 Z M5,5 L6,5").isClosed)
    }

    @Test
    fun `rejects unsupported or malformed data`() {
        assertThrows(IllegalArgumentException::class.java) { PathDataParser.parse("M0,0 X1,1") }
        assertThrows(IllegalArgumentException::class.java) { PathDataParser.parse("0,0 L1,1") }
        assertThrows(IllegalArgumentException::class.java) { PathDataParser.parse("M0,0 L1") }
    }
}
