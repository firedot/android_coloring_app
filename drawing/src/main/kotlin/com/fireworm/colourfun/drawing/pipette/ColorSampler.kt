package com.fireworm.colourfun.drawing.pipette

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import com.fireworm.colourfun.drawing.regions.RegionMap
import kotlin.math.roundToInt

/**
 * Picks the colour the child sees at a document point, for the pipette. Always opaque.
 *
 * - Unpainted paper gives the paper colour.
 * - On the outline it samples the nearest non-outline pixel, never the black line.
 * - Semi-transparent paint is composited over the paper, matching the screen.
 *
 * Main thread only (reads the paint bitmap).
 */
class ColorSampler(
    private val paint: Bitmap,
    private val paperColor: Int,
    private val regions: RegionMap,
) {
    fun sample(x: Float, y: Float): Int {
        val i = regions.nearestRegionPixel(x, y)
        val px = if (i >= 0) i % regions.width else x.toInt().coerceIn(0, paint.width - 1)
        val py = if (i >= 0) i / regions.width else y.toInt().coerceIn(0, paint.height - 1)
        return overPaper(paint[px, py])
    }

    private fun overPaper(color: Int): Int {
        val alpha = Color.alpha(color)
        if (alpha == 255) return color
        if (alpha == 0) return paperColor
        val t = alpha / 255f
        fun blend(paint: Int, paper: Int) = (paint * t + paper * (1f - t)).roundToInt()
        return Color.rgb(
            blend(Color.red(color), Color.red(paperColor)),
            blend(Color.green(color), Color.green(paperColor)),
            blend(Color.blue(color), Color.blue(paperColor)),
        )
    }
}
