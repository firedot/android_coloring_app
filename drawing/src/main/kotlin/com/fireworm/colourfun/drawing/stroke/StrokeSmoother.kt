package com.fireworm.colourfun.drawing.stroke

import com.fireworm.colourfun.drawing.brushes.StrokePoint
import kotlin.math.PI
import kotlin.math.abs

/** 1-euro filter on both axes: smooths jitter when slow, keeps up when fast. */
internal class StrokeSmoother(
    minCutoffHz: Float = MIN_CUTOFF_HZ,
    beta: Float = BETA,
) {
    private val x = OneEuroFilter(minCutoffHz, beta)
    private val y = OneEuroFilter(minCutoffHz, beta)

    fun filter(p: StrokePoint): StrokePoint =
        p.copy(x = x.filter(p.x, p.timeMs), y = y.filter(p.y, p.timeMs))

    private companion object {
        // Tuned for document pixels; raise MIN_CUTOFF_HZ if slow strokes lag behind the finger.
        const val MIN_CUTOFF_HZ = 4f
        const val BETA = 0.01f
    }
}

/** See Casiez et al., "1€ Filter" (CHI 2012). */
internal class OneEuroFilter(
    private val minCutoffHz: Float,
    private val beta: Float,
    private val derivativeCutoffHz: Float = 1f,
) {
    private var initialized = false
    private var value = 0f
    private var derivative = 0f
    private var lastTimeMs = 0L

    fun filter(raw: Float, timeMs: Long): Float {
        if (!initialized) {
            initialized = true
            value = raw
            lastTimeMs = timeMs
            return raw
        }
        // Historical points can share a timestamp; avoid dividing by zero.
        val dt = (timeMs - lastTimeMs).coerceAtLeast(1L) / 1000f
        lastTimeMs = timeMs

        derivative += smoothing(dt, derivativeCutoffHz) * ((raw - value) / dt - derivative)
        val cutoff = minCutoffHz + beta * abs(derivative)
        value += smoothing(dt, cutoff) * (raw - value)
        return value
    }

    private fun smoothing(dt: Float, cutoffHz: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoffHz)
        return 1f / (1f + tau / dt)
    }
}
