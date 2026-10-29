package com.fireworm.colourfun.drawing.brushes

import android.graphics.Color
import android.graphics.PorterDuff

/**
 * Removes paint back to the paper: an opaque round line in the scratch layer, merged with DstOut.
 * The paper and the outline are never affected.
 */
class Eraser : Brush {
    private val line = Pen()

    override val opacity = 1f
    override val blendMode = PorterDuff.Mode.DST_OUT

    // Only the scratch coverage matters for DstOut; draw it opaque whatever colour is selected.
    override fun begin(ctx: StrokeContext, p: StrokePoint) = line.begin(ctx.opaque(), p)

    override fun segment(ctx: StrokeContext, points: List<StrokePoint>) = line.segment(ctx.opaque(), points)

    private fun StrokeContext.opaque() = StrokeContext(canvas, Color.BLACK, widthPx, random, below)
}
