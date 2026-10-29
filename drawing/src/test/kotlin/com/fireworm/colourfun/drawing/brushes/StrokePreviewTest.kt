package com.fireworm.colourfun.drawing.brushes

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The live preview of a stroke must look exactly like the stroke once merged into the paint. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrokePreviewTest {

    @Test
    fun `preview matches the merged result for every medium`() {
        Medium.entries.forEach { medium ->
            BrushSize.entries.forEach { size -> checkPreviewMatchesMerge(BrushSpec(medium, size)) }
        }
    }

    private fun checkPreviewMatchesMerge(spec: BrushSpec) {
        val engine = FixedStroke.engine()
        FixedStroke.paintBands(engine)
        val before = FixedStroke.render(engine)
        FixedStroke.draw(engine, spec, COLOR)
        val preview = FixedStroke.render(engine)
        engine.endStroke()
        val merged = FixedStroke.render(engine)

        // endStroke may still add the last bit of the stroke where the finger lifted.
        val end = FixedStroke.points.last()
        val ignoreRadius = spec.baseWidth * 2f
        var changed = 0
        for (y in 0 until preview.height) for (x in 0 until preview.width) {
            if (hypot(x - end.x, y - end.y) < ignoreRadius) continue
            val a = preview.getPixel(x, y)
            val b = merged.getPixel(x, y)
            if (a != b) changed++
            assertTrue(
                "$spec: preview ${hex(a)} vs merged ${hex(b)} at ($x, $y)",
                close(Color.red(a), Color.red(b)) && close(Color.green(a), Color.green(b)) && close(Color.blue(a), Color.blue(b)),
            )
        }
        assertTrue("$spec: stroke left no mark", !merged.sameAs(before))
    }

    /** SrcOver in 8 bits rounds slightly differently when grouped the other way. */
    private fun close(a: Int, b: Int) = abs(a - b) <= 2

    private fun hex(color: Int) = "#%08X".format(color)

    private companion object {
        const val COLOR = 0xFF1565C0.toInt()
    }
}
