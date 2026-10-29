package com.fireworm.colourfun.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private val Handle = Color(0xFFC0803F)
private val Ferrule = Color(0xFFB0B0B0)

/**
 * Original paint-brush icon for the tools button: a wooden handle, a metal ferrule and a bristle tip
 * dipped in [tipColor], drawn upright and tilted 45° so the tip points up and to the right.
 */
fun paintBrushIcon(tipColor: Color): ImageVector =
    ImageVector.Builder(
        name = "PaintBrush",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 48f,
        viewportHeight = 48f,
    ).apply {
        group(rotate = 45f, pivotX = 24f, pivotY = 24f) {
            // Handle: a long rounded bar.
            path(fill = SolidColor(Handle), stroke = SolidColor(IconOutline), strokeLineWidth = 2f) {
                moveTo(21f, 27f)
                lineTo(27f, 27f)
                lineTo(27f, 42f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 21f, y1 = 42f)
                close()
            }
            // Ferrule: the metal band, slightly wider than the handle.
            path(fill = SolidColor(Ferrule), stroke = SolidColor(IconOutline), strokeLineWidth = 2f) {
                moveTo(19.5f, 19f)
                lineTo(28.5f, 19f)
                lineTo(28f, 27f)
                lineTo(20f, 27f)
                close()
            }
            // Bristles: a teardrop tip.
            path(fill = SolidColor(tipColor), stroke = SolidColor(IconOutline), strokeLineWidth = 2f) {
                moveTo(19.5f, 19f)
                curveTo(19.5f, 12f, 22f, 7.5f, 24f, 3.5f)
                curveTo(26f, 7.5f, 28.5f, 12f, 28.5f, 19f)
                close()
            }
        }
    }.build()
