package com.example.colourfun.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.example.colourfun.drawing.DrawingEngine
import com.example.colourfun.drawing.brushes.BrushSpec
import com.example.colourfun.drawing.brushes.StrokePoint
import com.example.colourfun.drawing.document.DocumentCoords

/**
 * Shows the engine's document fit-centred and feeds touches straight into the engine.
 *
 * One finger draws; a second finger cancels the stroke in progress. Space outside the document is
 * left to the caller's background.
 *
 * @param onStrokeActiveChange called with `true` when a stroke starts and `false` when it ends.
 * @param onFrameDrawn called after each draw with the fraction of the document that changed.
 */
@Composable
fun DrawingSurface(
    engine: DrawingEngine,
    brush: BrushSpec,
    color: Int,
    modifier: Modifier = Modifier,
    onStrokeActiveChange: (Boolean) -> Unit = {},
    onFrameDrawn: (dirtyFraction: Float) -> Unit = {},
) {
    // Read only inside the draw lambda, so a stroke update redraws without recomposing.
    val frameVersion = engine.frameVersion.collectAsState()
    val currentBrush by rememberUpdatedState(brush)
    val currentColor by rememberUpdatedState(color)
    val currentOnStrokeActiveChange by rememberUpdatedState(onStrokeActiveChange)
    val documentSize = engine.document.size
    val documentPixels = documentSize.width.toFloat() * documentSize.height

    val input = Modifier.pointerInput(engine) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val coords = DocumentCoords.fit(size.width.toFloat(), size.height.toFloat(), documentSize)
            engine.beginStroke(currentBrush, currentColor, down.toStrokePoint(coords))
            down.consume()
            currentOnStrokeActiveChange(true)

            var drawing = true
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (drawing && event.changes.any { it.id != down.id && it.pressed }) {
                        engine.cancelStroke()
                        drawing = false
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (drawing && change.positionChanged()) {
                        engine.extendStroke(change.strokePoints(coords))
                    }
                    change.consume()
                    if (!change.pressed) break
                }
                if (drawing) engine.endStroke()
                drawing = false
            } finally {
                // The gesture was interrupted (e.g. the surface left the screen).
                if (drawing) engine.cancelStroke()
                currentOnStrokeActiveChange(false)
            }
        }
    }

    Canvas(modifier.then(input)) {
        frameVersion.value
        val coords = DocumentCoords.fit(size.width, size.height, documentSize)
        withTransform({
            translate(coords.left, coords.top)
            scale(coords.scale, coords.scale, pivot = Offset.Zero)
        }) {
            drawIntoCanvas { engine.draw(it.nativeCanvas) }
        }
        val dirty = engine.consumeDirtyRect()
        onFrameDrawn(dirty.width() * dirty.height() / documentPixels)
    }
}

private fun PointerInputChange.toStrokePoint(coords: DocumentCoords) = StrokePoint(
    x = coords.toDocumentX(position.x),
    y = coords.toDocumentY(position.y),
    timeMs = uptimeMillis,
    pressure = pressure,
)

/** The MotionEvent's historical points since the last event, then the current one. */
private fun PointerInputChange.strokePoints(coords: DocumentCoords): List<StrokePoint> =
    historical.map {
        StrokePoint(coords.toDocumentX(it.position.x), coords.toDocumentY(it.position.y), it.uptimeMillis, pressure)
    } + toStrokePoint(coords)
