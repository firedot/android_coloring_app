package com.fireworm.colourfun.drawing.brushes

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader

/**
 * Graphite: jittered round stamps give a slightly uneven edge, and the paper grain shows through as
 * speckles. Merged at 60 %, so going over it again darkens it.
 */
class Pencil : Brush {
    override val opacity = 0.6f
    override val blendMode = PorterDuff.Mode.SRC_OVER
    override val grain: Shader get() = PaperGrain.shader

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
        val radius = ctx.widthPx / 2f * random.nextFloat(0.93f, 1f)
        ctx.canvas.drawCircle(x, y, radius, paint)
        bounds.union(p.x, p.y)
    }

    private companion object {
        /** Stamp offset, as a share of the width (standard deviation). */
        const val JITTER = 0.025f

        /** Dirty-rect width allowance for jittered stamps (4 standard deviations each side). */
        const val JITTER_BOUND = 1f + 8f * JITTER

        /** Fine paper tooth, created on first use (main thread only, like all drawing). */
        val PaperGrain by lazy(LazyThreadSafetyMode.NONE) { Grain(size = 128, seed = 7, floor = 0.05f, blurPasses = 1, gamma = 0.45f) }
    }
}
