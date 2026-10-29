package com.fireworm.colourfun.generator.geometry

import kotlin.math.hypot

/** A point or offset in document pixels. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(factor: Float) = Vec2(x * factor, y * factor)

    val length: Float get() = hypot(x, y)

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}
