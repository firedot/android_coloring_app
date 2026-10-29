package com.fireworm.colourfun.ui.canvas

import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
 * With a drawing tool or the eraser, one finger draws and a second finger cancels the stroke in
 * progress, as does a touch the system flags as a palm (API 33+). With any other tool (fill,
 * pipette), a touch reports its document position to [onTap]. Space outside the document is left
 * to the caller's background.
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
    var strokeActive by remember { mutableStateOf(false) }

    // While a stroke is in progress, a frame tick lets brushes like the spray build up where the finger
    // rests. Frame time and touch times share the uptime clock.
    LaunchedEffect(engine, strokeActive) {
        if (strokeActive) while (true) withFrameNanos { engine.tick(it / 1_000_000) }
    }

    val input = Modifier.pointerInput(engine) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val coords = DocumentCoords.fit(size.width.toFloat(), size.height.toFloat(), documentSize)
            val spec = currentTool.strokeSpec
            if (spec == null) {
                // Fill and pipette act on touch-down for instant feedback; the rest of the gesture is ignored.
                currentOnTap(coords.toDocumentX(down.position.x), coords.toDocumentY(down.position.y))
                return@awaitEachGesture
            }
            engine.beginStroke(spec, currentColor, down.toStrokePoint(coords), currentStayInsideLines)
            strokeActive = true
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
                    if (drawing && !change.pressed && event.motionEvent?.wasCanceled() == true) {
                        // The system decided this touch was a palm: drop the stroke rather than keep it.
                        engine.cancelStroke()
                        drawing = false
                    }
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
                strokeActive = false
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

/** API 33+: the lifted pointer was accidental, e.g. a resting palm, and its gesture should be undone. */
internal fun MotionEvent.wasCanceled(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (flags and MotionEvent.FLAG_CANCELED) != 0

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
