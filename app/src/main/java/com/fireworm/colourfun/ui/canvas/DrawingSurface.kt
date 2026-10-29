package com.fireworm.colourfun.ui.canvas

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
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.brushes.StrokePoint
import com.fireworm.colourfun.drawing.document.DocumentCoords

/**
 * Shows the engine's document fit-centred and feeds touches straight into the engine.
 *
 * With a drawing tool, one finger draws and a second finger cancels the stroke in progress. With any
 * other tool (fill, pipette), a touch reports its document position to [onTap]. Space outside the
 * document is left to the caller's background.
 *
 * @param onTap called with the document position of a tap with a non-drawing tool.
 * @param onStrokeActiveChange called with `true` when a stroke starts and `false` when it ends.
 * @param onFrameDrawn called after each draw with the fraction of the document that changed.
 */
@Composable
fun DrawingSurface(
    engine: DrawingEngine,
    tool: Tool,
    color: Int,
    stayInsideLines: Boolean,
    onTap: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    onStrokeActiveChange: (Boolean) -> Unit = {},
    onFrameDrawn: (dirtyFraction: Float) -> Unit = {},
) {
    // Read only inside the draw lambda, so a stroke update redraws without recomposing.
    val frameVersion = engine.frameVersion.collectAsState()
    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(color)
    val currentStayInsideLines by rememberUpdatedState(stayInsideLines)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnStrokeActiveChange by rememberUpdatedState(onStrokeActiveChange)
    val documentSize = engine.document.size
    val documentPixels = documentSize.width.toFloat() * documentSize.height

    val input = Modifier.pointerInput(engine) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val coords = DocumentCoords.fit(size.width.toFloat(), size.height.toFloat(), documentSize)
            val drawTool = when (val selected = currentTool) {
                is Tool.Draw -> selected
                Tool.Fill, Tool.Pipette -> {
                    // Act on touch-down for instant feedback; the rest of the gesture is ignored.
                    currentOnTap(coords.toDocumentX(down.position.x), coords.toDocumentY(down.position.y))
                    return@awaitEachGesture
                }
            }
            engine.beginStroke(drawTool.spec, currentColor, down.toStrokePoint(coords), currentStayInsideLines)
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
