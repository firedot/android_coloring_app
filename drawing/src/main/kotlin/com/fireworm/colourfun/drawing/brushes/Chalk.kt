package com.fireworm.colourfun.drawing.brushes

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader

/**
 * Chalk: broad stamps masked by a coarse paper grain with deep pits, so the paper shows through in
 * random gaps and the edge crumbles. Nearly opaque.
 */
class Chalk : Brush {
    override val opacity = 0.95f
    override val blendMode = PorterDuff.Mode.SRC_OVER
    override val grain: Shader get() = CoarseGrain.shader

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun begin(ctx: StrokeContext, p: StrokePoint): RectF {
        paint.color = ctx.color
        val bounds = RectF(p.x, p.y, p.x, p.y)
        stamp(ctx, p, bounds)
        return bounds.outset(ctx.widthPx * JITTER_BOUND)
    }

    override fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF {
        val first = points.first()
        val bounds = RectF(first.x, first.y, first.x, first.y)
        for (i in 1 until points.size) stamp(ctx, points[i], bounds)
        return bounds.outset(ctx.widthPx * JITTER_BOUND)
    }

    private fun stamp(ctx: StrokeContext, p: StrokePoint, bounds: RectF) {
        val random = ctx.random
        val x = p.x + random.nextGaussian() * ctx.widthPx * JITTER
        val y = p.y + random.nextGaussian() * ctx.widthPx * JITTER
        ctx.canvas.drawCircle(x, y, ctx.widthPx / 2f * random.nextFloat(0.9f, 1f), paint)
        bounds.union(p.x, p.y)
    }

    private companion object {
        /** Stamp offset, as a share of the width (standard deviation). */
        const val JITTER = 0.03f

        /** Dirty-rect width allowance for jittered stamps (4 standard deviations each side). */
        const val JITTER_BOUND = 1f + 8f * JITTER

        /** Coarse paper where about a third of the pits stay empty; created on first use (main thread only). */
        val CoarseGrain by lazy(LazyThreadSafetyMode.NONE) {
            Grain(size = 128, seed = 11, floor = 0f, blurPasses = 2, gamma = 0.5f, cutoff = 0.35f)
        }
    }
}
