package com.fireworm.colourfun.drawing.brushes

import android.graphics.PorterDuff
import android.graphics.RectF
import kotlin.math.hypot

/**
 * The Brush medium: soft round stamps whose width follows the smoothed speed, swelling when the
 * finger slows down and thinning when it moves fast, like an ink brush.
 */
class InkBrush : Brush {
    override val opacity = 1f
    override val blendMode = PorterDuff.Mode.SRC_OVER

    private val stamp = SoftStamp()

    /** Low-pass filtered speed, in document pixels per ms. */
    private var speed = 0f

    override fun begin(ctx: StrokeContext, p: StrokePoint): RectF {
        val radius = radius(ctx)
        stamp.draw(ctx.canvas, p.x, p.y, radius, ctx.color, STAMP_ALPHA)
        return RectF(p.x - radius, p.y - radius, p.x + radius, p.y + radius)
    }

    override fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF {
        val first = points.first()
        val bounds = RectF(first.x, first.y, first.x, first.y)
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            val dt = (to.timeMs - from.timeMs).coerceAtLeast(1L)
            speed += SMOOTHING * (hypot(to.x - from.x, to.y - from.y) / dt - speed)
            val radius = radius(ctx)
            stamp.draw(ctx.canvas, to.x, to.y, radius, ctx.color, STAMP_ALPHA)
            bounds.union(to.x - radius, to.y - radius, to.x + radius, to.y + radius)
        }
        return bounds
    }

    /** Full width at rest, down to [MIN_WIDTH] of it at [FAST] base pixels per ms and above. */
    private fun radius(ctx: StrokeContext): Float {
        val scale = ctx.widthPx / BASE_WIDTH_REFERENCE
        val fastness = (speed / (FAST * scale.coerceAtLeast(0.25f))).coerceIn(0f, 1f)
        return ctx.widthPx / 2f * (1f - (1f - MIN_WIDTH) * fastness) * STAMP_SCALE
    }

    private companion object {
        const val STAMP_ALPHA = 0.45f

        /** The soft stamp fades out over its outer half; scale it up so the solid part reaches the width. */
        const val STAMP_SCALE = 1.4f
        const val SMOOTHING = 0.15f
        const val MIN_WIDTH = 0.45f
        const val FAST = 2.5f

        /** Speed thresholds are tuned for a 24 px wide brush. */
        const val BASE_WIDTH_REFERENCE = 24f
    }
}
