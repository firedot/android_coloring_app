package com.example.colourfun.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.example.colourfun.drawing.document.Document
import kotlin.math.min

/**
 * Shows a [Document] fit-centred in the available space, composing paper → paint → outline.
 * Space outside the document is left to the caller's background.
 */
@Composable
fun DrawingSurface(document: Document, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val docWidth = document.size.width.toFloat()
        val docHeight = document.size.height.toFloat()
        val fit = min(size.width / docWidth, size.height / docHeight)
        val left = (size.width - docWidth * fit) / 2f
        val top = (size.height - docHeight * fit) / 2f

        withTransform({
            translate(left, top)
            scale(fit, fit, pivot = Offset.Zero)
        }) {
            drawIntoCanvas { document.draw(it.nativeCanvas) }
        }
    }
}
