package com.fireworm.colourfun.drawing.brushes

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.core.graphics.get
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A loaded paint brush: wide soft stamps that pick up about 15 % of the paint they pass over, so
 * colours smear into each other, and that run out of paint along the stroke (fainter, narrower).
 */
class PaintBrush : Brush {
    override val opacity = 1f
    override val blendMode = PorterDuff.Mode.SRC_OVER

    private val stamp = SoftStamp()
    private var color = 0
    private var travelled = 0f

    override fun begin(ctx: StrokeContext, p: StrokePoint): RectF {
        color = ctx.color
        travelled = 0f
        return dab(ctx, p)
    }

    override fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF {
        val first = points.first()
        val bounds = RectF(first.x, first.y, first.x, first.y)
        for (i in 1 until points.size) {
            travelled += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            bounds.union(dab(ctx, points[i]))
        }
        return bounds
    }

    private fun dab(ctx: StrokeContext, p: StrokePoint): RectF {
        pickUp(ctx, p)
        // Runs dry over about 40 brush widths, but never fully.
        val load = (1f - travelled / (ctx.widthPx * DRY_AFTER_WIDTHS)).coerceIn(0f, 1f)
        val radius = ctx.widthPx / 2f * STAMP_SCALE * (MIN_WIDTH + (1f - MIN_WIDTH) * load)
        stamp.draw(ctx.canvas, p.x, p.y, radius, color, STAMP_ALPHA * (MIN_ALPHA + (1f - MIN_ALPHA) * load))
        return RectF(p.x - radius, p.y - radius, p.x + radius, p.y + radius)
    }

    /** Mixes the brush colour towards the paint under [p], weighted by how much paint is there. */
    private fun pickUp(ctx: StrokeContext, p: StrokePoint) {
        val x = p.x.toInt()
        val y = p.y.toInt()
        if (x !in 0 until ctx.below.width || y !in 0 until ctx.below.height) return
        val under = ctx.below[x, y]
        val t = PICK_UP * Color.alpha(under) / 255f
        if (t == 0f) return
        fun mix(a: Int, b: Int) = (a + (b - a) * t).roundToInt()
        color = Color.argb(
            Color.alpha(color),
            mix(Color.red(color), Color.red(under)),
            mix(Color.green(color), Color.green(under)),
            mix(Color.blue(color), Color.blue(under)),
        )
    }

    private companion object {
        const val PICK_UP = 0.15f
        const val STAMP_ALPHA = 0.5f
        const val STAMP_SCALE = 1.5f
        const val DRY_AFTER_WIDTHS = 40f
        const val MIN_WIDTH = 0.75f
        const val MIN_ALPHA = 0.35f
    }
}
