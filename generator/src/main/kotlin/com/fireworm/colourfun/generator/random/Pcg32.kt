package com.fireworm.colourfun.generator.random

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * PCG32 (XSH RR) random numbers: small, fast and identical on every device and JVM, so a stroke's
 * seed fixes exactly how it looks.
 */
class Pcg32(seed: Long, stream: Long = DEFAULT_STREAM) {
    private var state = 0L
    private val increment = (stream shl 1) or 1L

    init {
        nextInt()
        state += seed
        nextInt()
    }

    fun nextInt(): Int {
        val old = state
        state = old * MULTIPLIER + increment
        val xorShifted = (((old ushr 18) xor old) ushr 27).toInt()
        val rotation = (old ushr 59).toInt()
        return Integer.rotateRight(xorShifted, rotation)
    }

    /** Uniform in `[0, bound)`, without modulo bias. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        val range = bound.toUInt()
        // Reject the few values that would make the low end more likely.
        val threshold = (0u - range) % range
        while (true) {
            val r = nextInt().toUInt()
            if (r >= threshold) return (r % range).toInt()
        }
    }

    /** Uniform in `[0, 1)`. */
    fun nextFloat(): Float = (nextInt() ushr 8) * (1f / (1 shl 24))

    /** Uniform in `[from, until)`. */
    fun nextFloat(from: Float, until: Float): Float = from + (until - from) * nextFloat()

    /** Standard normal (Box–Muller). */
    fun nextGaussian(): Float {
        val u1 = 1f - nextFloat()
        val u2 = nextFloat()
        return sqrt(-2f * ln(u1)) * cos(2f * PI.toFloat() * u2)
    }

    private companion object {
        const val MULTIPLIER = 6364136223846793005L
        val DEFAULT_STREAM = 0xDA3E39CB94B95BDBuL.toLong()
    }
}
