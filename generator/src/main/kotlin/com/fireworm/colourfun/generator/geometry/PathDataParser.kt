package com.fireworm.colourfun.generator.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Parses SVG path data (the `d` attribute) into a [PathShape]: M L H V C S Q T A Z, absolute and
 * relative. Quadratic curves and arcs become cubics. Nothing else from SVG is supported.
 */
object PathDataParser {

    fun parse(data: String): PathShape = Parser(data).run()

    private class Parser(private val data: String) {
        private var pos = 0
        private val out = mutableListOf<PathCommand>()
        private var current = Vec2.Zero
        private var subpathStart = Vec2.Zero

        /** Second control point of the last C/S (or the Q/T control), for S and T reflection. */
        private var lastCubicControl: Vec2? = null
        private var lastQuadControl: Vec2? = null

        fun run(): PathShape {
            var command = ' '
            while (true) {
                skipSeparators()
                if (pos >= data.length) break
                val c = data[pos]
                if (c.isLetter()) {
                    command = c
                    pos++
                } else {
                    require(command != ' ') { "Path data must start with a command: \"$data\"" }
                    // Extra coordinates after M/m are implicit L/l.
                    if (command == 'M') command = 'L' else if (command == 'm') command = 'l'
                }
                execute(command)
            }
            return PathShape(out)
        }

        private fun execute(command: Char) {
            val relative = command.isLowerCase()
            val base = if (relative) current else Vec2.Zero
            when (command.uppercaseChar()) {
                'M' -> {
                    current = base + point()
                    subpathStart = current
                    out += PathCommand.MoveTo(current)
                    clearControls()
                }
                'L' -> lineTo(base + point())
                'H' -> lineTo(Vec2(number() + (if (relative) current.x else 0f), current.y))
                'V' -> lineTo(Vec2(current.x, number() + (if (relative) current.y else 0f)))
                'C' -> {
                    val c1 = base + point()
                    val c2 = base + point()
                    cubicTo(c1, c2, base + point())
                }
                'S' -> {
                    val c1 = lastCubicControl?.let { current * 2f - it } ?: current
                    val c2 = base + point()
                    cubicTo(c1, c2, base + point())
                }
                'Q' -> {
                    val control = base + point()
                    quadTo(control, base + point())
                }
                'T' -> {
                    val control = lastQuadControl?.let { current * 2f - it } ?: current
                    quadTo(control, base + point())
                }
                'A' -> {
                    val rx = number()
                    val ry = number()
                    val rotation = number()
                    val largeArc = flag()
                    val sweep = flag()
                    arcTo(rx, ry, rotation, largeArc, sweep, base + point())
                }
                'Z' -> {
                    out += PathCommand.Close
                    current = subpathStart
                    clearControls()
                }
                else -> throw IllegalArgumentException("Unsupported path command '$command' in \"$data\"")
            }
        }

        private fun lineTo(p: Vec2) {
            out += PathCommand.LineTo(p)
            current = p
            clearControls()
        }

        private fun cubicTo(c1: Vec2, c2: Vec2, p: Vec2) {
            out += PathCommand.CubicTo(c1, c2, p)
            current = p
            lastCubicControl = c2
            lastQuadControl = null
        }

        private fun quadTo(control: Vec2, p: Vec2) {
            // Degree elevation: the same curve as a cubic.
            val c1 = current + (control - current) * (2f / 3f)
            val c2 = p + (control - p) * (2f / 3f)
            out += PathCommand.CubicTo(c1, c2, p)
            current = p
            lastQuadControl = control
            lastCubicControl = null
        }

        /** SVG endpoint arc → cubics of at most 90° each (SVG implementation notes, F.6). */
        private fun arcTo(rxIn: Float, ryIn: Float, rotationDeg: Float, largeArc: Boolean, sweep: Boolean, p: Vec2) {
            if (current == p) return
            var rx = abs(rxIn)
            var ry = abs(ryIn)
            if (rx == 0f || ry == 0f) return lineTo(p)

            val phi = rotationDeg * PI.toFloat() / 180f
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            val dx = (current.x - p.x) / 2f
            val dy = (current.y - p.y) / 2f
            val x1 = cosPhi * dx + sinPhi * dy
            val y1 = -sinPhi * dx + cosPhi * dy

            val lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
            if (lambda > 1f) {
                rx *= sqrt(lambda)
                ry *= sqrt(lambda)
            }
            val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            val denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
            var factor = sqrt((numerator / denominator).coerceAtLeast(0f))
            if (largeArc == sweep) factor = -factor
            val cx1 = factor * rx * y1 / ry
            val cy1 = -factor * ry * x1 / rx
            val cx = cosPhi * cx1 - sinPhi * cy1 + (current.x + p.x) / 2f
            val cy = sinPhi * cx1 + cosPhi * cy1 + (current.y + p.y) / 2f

            val start = angle(1f, 0f, (x1 - cx1) / rx, (y1 - cy1) / ry)
            var delta = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
            if (!sweep && delta > 0) delta -= 2f * PI.toFloat()
            if (sweep && delta < 0) delta += 2f * PI.toFloat()

            val segments = ceil(abs(delta) / (PI.toFloat() / 2f)).toInt().coerceAtLeast(1)
            val step = delta / segments
            val k = 4f / 3f * tan(step / 4f)
            var theta = start
            fun ellipsePoint(t: Float) = Vec2(
                cx + rx * cos(t) * cosPhi - ry * sin(t) * sinPhi,
                cy + rx * cos(t) * sinPhi + ry * sin(t) * cosPhi,
            )
            fun derivative(t: Float) = Vec2(
                -rx * sin(t) * cosPhi - ry * cos(t) * sinPhi,
                -rx * sin(t) * sinPhi + ry * cos(t) * cosPhi,
            )
            repeat(segments) { i ->
                val next = theta + step
                val from = ellipsePoint(theta)
                val to = if (i == segments - 1) p else ellipsePoint(next)
                cubicTo(from + derivative(theta) * k, to - derivative(next) * k, to)
                theta = next
            }
            clearControls()
        }

        private fun angle(ux: Float, uy: Float, vx: Float, vy: Float): Float {
            val dot = ux * vx + uy * vy
            val length = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            val a = acos((dot / length).coerceIn(-1f, 1f))
            return if (ux * vy - uy * vx < 0) -a else a
        }

        private fun clearControls() {
            lastCubicControl = null
            lastQuadControl = null
        }

        private fun point() = Vec2(number(), number())

        /** Arc flags are a single 0 or 1 and may be written without separators ("a1 1 0 01 2 2"). */
        private fun flag(): Boolean {
            skipSeparators()
            require(pos < data.length && data[pos] in "01") { "Expected an arc flag at $pos in \"$data\"" }
            return data[pos++] == '1'
        }

        private fun number(): Float {
            skipSeparators()
            val start = pos
            if (pos < data.length && data[pos] in "+-") pos++
            var digits = false
            while (pos < data.length && data[pos].isDigit()) { pos++; digits = true }
            if (pos < data.length && data[pos] == '.') {
                pos++
                while (pos < data.length && data[pos].isDigit()) { pos++; digits = true }
            }
            require(digits) { "Expected a number at $start in \"$data\"" }
            if (pos < data.length && data[pos] in "eE") {
                val mark = pos
                pos++
                if (pos < data.length && data[pos] in "+-") pos++
                if (pos < data.length && data[pos].isDigit()) {
                    while (pos < data.length && data[pos].isDigit()) pos++
                } else {
                    pos = mark
                }
            }
            return data.substring(start, pos).toFloat()
        }

        private fun skipSeparators() {
            while (pos < data.length && (data[pos].isWhitespace() || data[pos] == ',')) pos++
        }
    }
}
