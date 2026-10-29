package com.example.colourfun.drawing.brushes

import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.RectF

/** A point of a stroke in document pixels. */
data class StrokePoint(val x: Float, val y: Float, val timeMs: Long, val pressure: Float = 1f)

/** What a brush draws with for one stroke. [canvas] is over the stroke scratch layer. */
class StrokeContext(val canvas: Canvas, val color: Int, val widthPx: Float)

/** Renders one stroke into the scratch layer. A new instance is created per stroke. */
interface Brush {
    /** Applied when the stroke is merged into the paint layer, and to its live preview. */
    val opacity: Float
    val blendMode: PorterDuff.Mode

    /** Draws the first point. Returns the dirty rect in document pixels. */
    fun begin(ctx: StrokeContext, p: StrokePoint): RectF

    /** Draws through [points], where the first point is the last one already drawn. Returns the dirty rect. */
    fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF
}
