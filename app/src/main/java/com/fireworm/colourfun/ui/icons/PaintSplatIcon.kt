package com.fireworm.colourfun.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Dark enough to keep a white or yellow splat visible on white paper. */
internal val IconOutline = Color(0xFF3A3A3A)

private const val VIEWPORT = 48f
private const val CENTER_X = 24f
private const val CENTER_Y = 25f

/** Alternating lobe tips and valleys around the blob, in viewport units; uneven so it looks splashed. */
private val LobeRadii = floatArrayOf(16f, 14f, 17f, 13f, 15.5f, 14.5f, 16.5f, 13.5f)
private const val VALLEY_RADIUS = 10.5f

/** Droplets flung around the blob: (x, y, radius). */
private val Droplets = listOf(
    Triple(6.5f, 10f, 2.4f),
    Triple(42f, 8.5f, 1.8f),
    Triple(44f, 37f, 2.6f),
    Triple(7.5f, 41.5f, 1.7f),
)

/**
 * Original paint-splat icon for the palette button: a lobed blob with a few droplets, filled with
 * [color] and outlined. Build once per colour (`remember(color)`).
 */
fun paintSplatIcon(color: Color): ImageVector =
    ImageVector.Builder(
        name = "PaintSplat",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply {
        path(fill = SolidColor(color), stroke = SolidColor(IconOutline), strokeLineWidth = 2f) {
            blob()
            for ((x, y, r) in Droplets) circle(x, y, r)
        }
    }.build()

/** A closed, smooth blob through the lobe and valley points (quadratic curves between midpoints). */
private fun PathBuilder.blob() {
    val points = buildList {
        for (i in LobeRadii.indices) {
            val lobe = 2 * PI * i / LobeRadii.size - PI / 2
            val valley = lobe + PI / LobeRadii.size
            add(polar(lobe, LobeRadii[i]))
            add(polar(valley, VALLEY_RADIUS))
        }
    }
    fun mid(a: Pair<Float, Float>, b: Pair<Float, Float>) = (a.first + b.first) / 2f to (a.second + b.second) / 2f
    val start = mid(points.last(), points.first())
    moveTo(start.first, start.second)
    for (i in points.indices) {
        val control = points[i]
        val end = mid(control, points[(i + 1) % points.size])
        quadTo(control.first, control.second, end.first, end.second)
    }
    close()
}

private fun polar(angle: Double, radius: Float): Pair<Float, Float> =
    CENTER_X + (radius * cos(angle)).toFloat() to CENTER_Y + (radius * sin(angle)).toFloat()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -2 * r, dy1 = 0f)
    close()
}
