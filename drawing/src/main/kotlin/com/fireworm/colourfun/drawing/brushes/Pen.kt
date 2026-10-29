package com.fireworm.colourfun.drawing.brushes

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF

/** Crisp, opaque, constant-width line with round caps and joins. */
class Pen : Brush {
    override val opacity = 1f
    override val blendMode = PorterDuff.Mode.SRC_OVER

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun begin(ctx: StrokeContext, p: StrokePoint): RectF {
        paint.color = ctx.color
        paint.strokeWidth = ctx.widthPx
        // With a round cap this draws a dot, so a tap leaves a mark.
        ctx.canvas.drawPoint(p.x, p.y, paint)
        return RectF(p.x, p.y, p.x, p.y).outset(ctx.widthPx)
    }

    override fun segment(ctx: StrokeContext, points: List<StrokePoint>): RectF {
        val first = points.first()
        val bounds = RectF(first.x, first.y, first.x, first.y)
        // One path per batch instead of one line per point pair, so anti-aliased caps
        // do not pile up along the stroke.
        path.rewind()
        path.moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
            bounds.union(points[i].x, points[i].y)
        }
        ctx.canvas.drawPath(path, paint)
        return bounds.outset(ctx.widthPx)
    }
}
