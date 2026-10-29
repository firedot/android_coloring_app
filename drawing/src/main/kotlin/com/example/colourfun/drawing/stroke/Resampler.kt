package com.example.colourfun.drawing.stroke

import com.example.colourfun.drawing.brushes.StrokePoint
import kotlin.math.hypot

/** Turns irregular input points into points [spacing] document pixels apart along the stroke. */
internal class Resampler(private val spacing: Float) {
    private lateinit var lastInput: StrokePoint
    private var carried = 0f

    /** The last point handed out, which the next batch continues from. */
    lateinit var lastEmitted: StrokePoint
        private set

    fun start(p: StrokePoint) {
        lastInput = p
        lastEmitted = p
        carried = 0f
    }

    fun add(p: StrokePoint, out: MutableList<StrokePoint>) {
        var from = lastInput
        var remaining = hypot(p.x - from.x, p.y - from.y)
        while (carried + remaining >= spacing) {
            val step = spacing - carried
            val q = lerp(from, p, step / remaining)
            out += q
            lastEmitted = q
            from = q
            remaining -= step
            carried = 0f
        }
        carried += remaining
        lastInput = p
    }

    /** Emits the final input point so the stroke ends where the finger lifted. */
    fun finish(out: MutableList<StrokePoint>) {
        if (carried > 0f) {
            out += lastInput
            lastEmitted = lastInput
            carried = 0f
        }
    }

    private fun lerp(a: StrokePoint, b: StrokePoint, t: Float) = StrokePoint(
        x = a.x + (b.x - a.x) * t,
        y = a.y + (b.y - a.y) * t,
        timeMs = a.timeMs + ((b.timeMs - a.timeMs) * t).toLong(),
        pressure = a.pressure + (b.pressure - a.pressure) * t,
    )
}
