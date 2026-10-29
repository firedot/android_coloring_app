package com.fireworm.colourfun.drawing.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import com.fireworm.colourfun.generator.random.Pcg32

/** A point of a stroke in document pixels. */
data class StrokePoint(val x: Float, val y: Float, val timeMs: Long, val pressure: Float = 1f)

/**
 * What a brush draws with for one stroke. [canvas] is over the stroke scratch layer. [random] is
 * seeded per stroke, so the same seed and points always render the same stroke. [below] is the
 * paint layer under the stroke, read-only, for brushes that pick up colour.
 */
class StrokeContext(val canvas: Canvas, val color: Int, val widthPx: Float, val random: Pcg32, val below: Bitmap)

/** Renders one stroke into the scratch layer. A new instance is created per stroke. */
interface Brush {
    /** Applied when the stroke is merged into the paint layer, and to its live preview. */
    val opacity: Float
    val blendMode: PorterDuff.Mode

    /** Paper grain the stroke is masked with (DstIn) at merge and in the preview, or `null`. */
    val grain: Shader? get() = null

    /** Draws the first point. Returns the dirty rect in document pixels. */
    fun begin(ctx: StrokeContext, p: StrokePoint): RectF

    /** Draws through [points], where the first point is the last one already drawn. Returns the dirty rect. */
    fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF

    /**
     * Called every frame while the finger is down, at [timeMs] on the same clock as the points, so a
     * brush can keep building up where the finger rests at [p]. Frames and input can arrive for the
     * same moment; a time-based brush counts each millisecond once. Returns the dirty rect; most
     * brushes draw nothing.
     */
    fun dwell(ctx: StrokeContext, p: StrokePoint, timeMs: Long): RectF = RectF()
}

/** Grows the rect by half a stroke width plus a pixel of anti-aliasing. */
internal fun RectF.outset(width: Float): RectF {
    val r = width / 2f + 1f
    inset(-r, -r)
    return this
}
