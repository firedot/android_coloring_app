package com.fireworm.colourfun.generator.geometry

/**
 * An affine transform without rotation or shear: `p' = (p.x * sx + tx, p.y * sy + ty)`. A negative
 * [sx] mirrors horizontally. Enough to place, scale and mirror parts.
 */
data class Transform(val sx: Float = 1f, val sy: Float = 1f, val tx: Float = 0f, val ty: Float = 0f) {

    fun apply(p: Vec2) = Vec2(p.x * sx + tx, p.y * sy + ty)

    /** This transform applied after [inner]: `(this then inner)(p) = this(inner(p))`. */
    operator fun times(inner: Transform) = Transform(
        sx = sx * inner.sx,
        sy = sy * inner.sy,
        tx = sx * inner.tx + tx,
        ty = sy * inner.ty + ty,
    )

    fun apply(shape: PathShape) = PathShape(
        shape.commands.map { command ->
            when (command) {
                is PathCommand.MoveTo -> PathCommand.MoveTo(apply(command.p))
                is PathCommand.LineTo -> PathCommand.LineTo(apply(command.p))
                is PathCommand.CubicTo -> PathCommand.CubicTo(apply(command.c1), apply(command.c2), apply(command.p))
                PathCommand.Close -> PathCommand.Close
            }
        },
    )

    companion object {
        val Identity = Transform()

        fun translate(offset: Vec2) = Transform(tx = offset.x, ty = offset.y)
        fun scale(factor: Float) = Transform(sx = factor, sy = factor)
        val MirrorX = Transform(sx = -1f)
    }
}
