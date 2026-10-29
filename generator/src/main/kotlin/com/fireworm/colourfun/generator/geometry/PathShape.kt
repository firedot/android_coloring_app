package com.fireworm.colourfun.generator.geometry

sealed interface PathCommand {
    data class MoveTo(val p: Vec2) : PathCommand
    data class LineTo(val p: Vec2) : PathCommand
    data class CubicTo(val c1: Vec2, val c2: Vec2, val p: Vec2) : PathCommand
    data object Close : PathCommand
}

/** An outline path in document pixels. Outline paths are always closed so fills cannot leak. */
data class PathShape(val commands: List<PathCommand>) {
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
    }
}
