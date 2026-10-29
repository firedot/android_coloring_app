package com.fireworm.colourfun.drawing.brushes

import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import kotlin.math.hypot

/**
 * A glowing rainbow line: the hue moves along the spectrum with distance, starting from the chosen
 * colour, with a light core, a blurred glow and a few sparkles.
 */
class MagicPen : Brush {
    override val opacity = 1f
    override val blendMode = PorterDuff.Mode.SRC_OVER

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val hsv = FloatArray(3)
    private var startHue = 0f
    private var travelled = 0f
    private var nextSparkleAt = 0f

    override fun begin(ctx: StrokeContext, p: StrokePoint): RectF {
        Color.colorToHSV(ctx.color, hsv)
        startHue = hsv[0]
        travelled = 0f
        nextSparkleAt = ctx.widthPx * SPARKLE_EVERY_WIDTHS
        glow.strokeWidth = ctx.widthPx * GLOW_WIDTH
        glow.maskFilter = BlurMaskFilter(ctx.widthPx * GLOW_BLUR, BlurMaskFilter.Blur.NORMAL)
        core.strokeWidth = ctx.widthPx * CORE_WIDTH
        sparkle.strokeWidth = (ctx.widthPx * 0.12f).coerceAtLeast(1f)
        sparkle.color = Color.WHITE
        piece(ctx, p, p)
        return reach(ctx, RectF(p.x, p.y, p.x, p.y))
    }

    override fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF {
        val first = points.first()
        val bounds = RectF(first.x, first.y, first.x, first.y)
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            travelled += hypot(to.x - from.x, to.y - from.y)
            piece(ctx, from, to)
            bounds.union(to.x, to.y)
            if (travelled >= nextSparkleAt) {
                nextSparkleAt += ctx.widthPx * SPARKLE_EVERY_WIDTHS * ctx.random.nextFloat(0.5f, 1.5f)
                sparkleNear(ctx, to)
            }
        }
        return reach(ctx, bounds)
    }

    /** One short piece of the line in the hue for the distance so far: glow first, then the core. */
    private fun piece(ctx: StrokeContext, from: StrokePoint, to: StrokePoint) {
        val hue = (startHue + travelled * DEGREES_PER_PX / ctx.widthPx.coerceAtLeast(1f) * BASE_WIDTH) % 360f
        hsv[0] = hue
        hsv[1] = 0.85f
        hsv[2] = 1f
        glow.color = Color.HSVToColor(GLOW_ALPHA, hsv)
        hsv[1] = 0.35f
        core.color = Color.HSVToColor(hsv)
        path.rewind()
        path.moveTo(from.x, from.y)
        path.lineTo(to.x, to.y)
        ctx.canvas.drawPath(path, glow)
        ctx.canvas.drawPath(path, core)
    }

    /** A small four-pointed star a little off the line. */
    private fun sparkleNear(ctx: StrokeContext, p: StrokePoint) {
        val random = ctx.random
        val x = p.x + random.nextGaussian() * ctx.widthPx * 0.8f
        val y = p.y + random.nextGaussian() * ctx.widthPx * 0.8f
        val arm = ctx.widthPx * random.nextFloat(0.3f, 0.6f)
        ctx.canvas.drawLine(x - arm, y, x + arm, y, sparkle)
        ctx.canvas.drawLine(x, y - arm, x, y + arm, sparkle)
    }

    /** Glow and sparkles reach well beyond the line itself. */
    private fun reach(ctx: StrokeContext, bounds: RectF): RectF {
        val r = ctx.widthPx * REACH_WIDTHS
        bounds.inset(-r, -r)
        return bounds
    }

    private companion object {
        const val GLOW_WIDTH = 1.4f
        const val GLOW_BLUR = 0.6f
        const val GLOW_ALPHA = 150
        const val CORE_WIDTH = 0.45f

        /** Hue change per document pixel for a [BASE_WIDTH] line; scaled so bigger lines cycle slower. */
        const val DEGREES_PER_PX = 0.5f
        const val BASE_WIDTH = 16f
        const val SPARKLE_EVERY_WIDTHS = 4f

        /** Sparkles sit up to ~4 standard deviations (3.2 widths) away plus an arm; the glow less. */
        const val REACH_WIDTHS = 4f
    }
}
