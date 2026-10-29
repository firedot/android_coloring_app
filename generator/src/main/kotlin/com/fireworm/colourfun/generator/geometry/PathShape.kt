package com.fireworm.colourfun.generator.geometry

sealed interface PathCommand {
    data class MoveTo(val p: Vec2) : PathCommand
    data class LineTo(val p: Vec2) : PathCommand
    data class CubicTo(val c1: Vec2, val c2: Vec2, val p: Vec2) : PathCommand
    data object Close : PathCommand
}

/** An axis-aligned box. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: Vec2 get() = Vec2((left + right) / 2f, (top + bottom) / 2f)

    fun union(other: Bounds) = Bounds(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )
}

/** An outline path in document pixels. Outline paths are always closed so fills cannot leak. */
data class PathShape(val commands: List<PathCommand>) {

    /** Every sub-path ends with [PathCommand.Close]. */
    val isClosed: Boolean
        get() {
            var open = false
            for (command in commands) {
                when (command) {
                    is PathCommand.MoveTo -> if (open) return false else open = true
                    PathCommand.Close -> open = false
                    else -> if (!open) return false
                }
            }
            return !open
        }

    /**
     * The sub-paths as closed polygons, each cubic split into [segmentsPerCubic] lines. Good enough
     * for bounds and the validation raster, not for drawing.
     */
    fun flatten(segmentsPerCubic: Int = 12): List<List<Vec2>> {
        val polygons = mutableListOf<List<Vec2>>()
        var current = mutableListOf<Vec2>()
        for (command in commands) {
            when (command) {
                is PathCommand.MoveTo -> {
                    if (current.size > 1) polygons += current
                    current = mutableListOf(command.p)
                }
                is PathCommand.LineTo -> current += command.p
                is PathCommand.CubicTo -> {
                    val p0 = current.last()
                    for (i in 1..segmentsPerCubic) current += cubicAt(p0, command.c1, command.c2, command.p, i / segmentsPerCubic.toFloat())
                }
                PathCommand.Close -> {
                    if (current.size > 1) polygons += current
                    current = mutableListOf(current.firstOrNull() ?: Vec2.Zero)
                }
            }
        }
        if (current.size > 1) polygons += current
        return polygons
    }

    /** Bounds of the flattened outline (curves stay within their control hull, so this is close). */
    fun bounds(): Bounds {
        val points = flatten().flatten()
        return Bounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }

    companion object {
        // Control-point distance that makes one cubic approximate a quarter ellipse.
        private const val KAPPA = 0.5522848f

        fun ellipse(center: Vec2, rx: Float, ry: Float): PathShape {
            val (cx, cy) = center
            val kx = KAPPA * rx
            val ky = KAPPA * ry
            return PathShape(
                listOf(
                    PathCommand.MoveTo(Vec2(cx + rx, cy)),
                    PathCommand.CubicTo(Vec2(cx + rx, cy + ky), Vec2(cx + kx, cy + ry), Vec2(cx, cy + ry)),
                    PathCommand.CubicTo(Vec2(cx - kx, cy + ry), Vec2(cx - rx, cy + ky), Vec2(cx - rx, cy)),
                    PathCommand.CubicTo(Vec2(cx - rx, cy - ky), Vec2(cx - kx, cy - ry), Vec2(cx, cy - ry)),
                    PathCommand.CubicTo(Vec2(cx + kx, cy - ry), Vec2(cx + rx, cy - ky), Vec2(cx + rx, cy)),
                    PathCommand.Close,
                ),
            )
        }

        private fun cubicAt(p0: Vec2, c1: Vec2, c2: Vec2, p: Vec2, t: Float): Vec2 {
            val u = 1f - t
            val a = u * u * u
            val b = 3f * u * u * t
            val c = 3f * u * t * t
            val d = t * t * t
            return Vec2(a * p0.x + b * c1.x + c * c2.x + d * p.x, a * p0.y + b * c1.y + c * c2.y + d * p.y)
        }
    }
}
