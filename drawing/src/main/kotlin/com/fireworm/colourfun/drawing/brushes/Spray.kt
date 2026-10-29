package com.fireworm.colourfun.drawing.brushes

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Spray can: fine particles in a Gaussian disc. The number of particles is proportional to time,
 * both while moving and while the finger rests, so holding still builds up a dense spot.
 */
class Spray : Brush {
    override val opacity = 1f
    override val blendMode = PorterDuff.Mode.SRC_OVER

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Time already sprayed up to, so input and frame ticks never count a millisecond twice. */
    private var sprayedUntilMs = Long.MIN_VALUE

    /** Particles still owed from earlier calls (fractions of a particle). */
    private var owed = 0f

    override fun begin(ctx: StrokeContext, p: StrokePoint): RectF {
        paint.color = ctx.color
        sprayedUntilMs = p.timeMs
        // A first puff, so a quick tap leaves a mark.
        spray(ctx, p, p, FIRST_PUFF_MS)
        return area(ctx, p.x, p.y, p.x, p.y)
    }

    override fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF {
        val first = points.first()
        val bounds = area(ctx, first.x, first.y, first.x, first.y)
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            val dt = to.timeMs - max(from.timeMs, sprayedUntilMs)
            sprayedUntilMs = max(sprayedUntilMs, to.timeMs)
            if (dt > 0) spray(ctx, from, to, dt)
            bounds.union(area(ctx, to.x, to.y, to.x, to.y))
        }
        return bounds
    }

    override fun dwell(ctx: StrokeContext, p: StrokePoint, timeMs: Long): RectF {
        val dt = min(timeMs - sprayedUntilMs, MAX_DWELL_MS)
        if (dt <= 0) return RectF()
        sprayedUntilMs = timeMs
        spray(ctx, p, p, dt)
        return area(ctx, p.x, p.y, p.x, p.y)
    }

    /** Sprays [dtMs] worth of particles spread along the line from [from] to [to]. */
    private fun spray(ctx: StrokeContext, from: StrokePoint, to: StrokePoint, dtMs: Long) {
        val random = ctx.random
        val sigma = ctx.widthPx / 4f
        val particleRadius = (0.6f + ctx.widthPx * PARTICLE_SIZE)
        owed += dtMs * ctx.widthPx * PARTICLES_PER_MS_PER_WIDTH
        val count = owed.toInt()
        owed -= count
        repeat(count) {
            val t = random.nextFloat()
            // Drawn again beyond two standard deviations, so the spray stays a round disc of the brush width.
            var dx: Float
            var dy: Float
            do {
                dx = random.nextGaussian() * sigma
                dy = random.nextGaussian() * sigma
            } while (hypot(dx, dy) > 2f * sigma)
            ctx.canvas.drawCircle(
                from.x + (to.x - from.x) * t + dx,
                from.y + (to.y - from.y) * t + dy,
                particleRadius * random.nextFloat(0.6f, 1.2f),
                paint,
            )
        }
    }

    private fun area(ctx: StrokeContext, left: Float, top: Float, right: Float, bottom: Float): RectF {
        val r = ctx.widthPx / 2f + (0.6f + ctx.widthPx * PARTICLE_SIZE) * 1.2f + 1f
        return RectF(left - r, top - r, right + r, bottom + r)
    }

    private companion object {
        const val PARTICLES_PER_MS_PER_WIDTH = 0.12f
        const val PARTICLE_SIZE = 0.01f
        const val FIRST_PUFF_MS = 40L

        /** A long frame stall still sprays at most this much at once. */
        const val MAX_DWELL_MS = 100L
    }
}
