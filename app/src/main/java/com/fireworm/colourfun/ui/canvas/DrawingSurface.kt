package com.fireworm.colourfun.ui.canvas

import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.brushes.StrokePoint
import com.fireworm.colourfun.drawing.viewport.Viewport
import kotlinx.coroutines.flow.StateFlow

/** A stroke this short when a second finger lands was the start of a pinch, so it is discarded. */
internal const val SHORT_STROKE_MS = 150L
internal val ShortStrokeTravel = 24.dp

/**
 * Shows the engine's document through [viewport] and feeds touches straight into the engine.
 *
 * - One finger uses the current tool. A drawing tool or the eraser strokes; fill and pipette report
 *   the touch-down position to [onTap] when the finger lifts, so the first finger of a pinch never
 *   fills.
 * - A second finger ends the stroke in progress: it is discarded if it is short (under
 *   [SHORT_STROKE_MS] and [ShortStrokeTravel]), otherwise kept. From then on the gesture zooms and
 *   pans through [onTransform] until all fingers are up, with no drawing even if one finger remains.
 * - A touch the system flags as a palm (API 33+) cancels the stroke.
 *
 * Space outside the document is left to the caller's background (letterboxing).
 *
 * @param onViewSize the canvas size in pixels, when it is laid out.
 * @param onTransform one frame of a pinch: zoom factor around the focus, then pan, in screen pixels.
 * @param onActiveChange `true` while a stroke or a pinch is in progress.
 * @param onFrameDrawn called after each draw with the fraction of the document that changed.
 */
@Composable
fun DrawingSurface(
    engine: DrawingEngine,
    viewport: StateFlow<Viewport?>,
    tool: Tool,
    color: Int,
    stayInsideLines: Boolean,
    onTap: (x: Float, y: Float) -> Unit,
    onViewSize: (width: Float, height: Float) -> Unit,
    onTransform: (factor: Float, focus: Offset, pan: Offset) -> Unit,
    modifier: Modifier = Modifier,
    onActiveChange: (Boolean) -> Unit = {},
    onFrameDrawn: (dirtyFraction: Float) -> Unit = {},
) {
    // Read only inside the draw lambda and the gesture loop, so strokes and zoom redraw without
    // recomposing.
    val frameVersion = engine.frameVersion.collectAsState()
    val currentViewport: State<Viewport?> = viewport.collectAsState()
    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(color)
    val currentStayInsideLines by rememberUpdatedState(stayInsideLines)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnActiveChange by rememberUpdatedState(onActiveChange)
    val documentSize = engine.document.size
    val documentPixels = documentSize.width.toFloat() * documentSize.height
    var strokeActive by remember { mutableStateOf(false) }

    // While a stroke is in progress, a frame tick lets brushes like the spray build up where the finger
    // rests. Frame time and touch times share the uptime clock.
    LaunchedEffect(engine, strokeActive) {
        if (strokeActive) while (true) withFrameNanos { engine.tick(it / 1_000_000) }
    }

    val input = Modifier.pointerInput(engine) {
        val shortTravel = ShortStrokeTravel.toPx()
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val view = currentViewport.value ?: return@awaitEachGesture
            val spec = currentTool.strokeSpec
            var mode = if (spec == null) GestureMode.Tap else GestureMode.Draw
            if (spec != null) {
                engine.beginStroke(spec, currentColor, down.toStrokePoint(view), currentStayInsideLines)
                strokeActive = true
            }
            currentOnActiveChange(true)
            var travel = 0f

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (mode != GestureMode.Transform && event.changes.count { it.pressed } >= 2) {
                        if (mode == GestureMode.Draw) {
                            val elapsed = event.changes.maxOf { it.uptimeMillis } - down.uptimeMillis
                            if (elapsed < SHORT_STROKE_MS && travel < shortTravel) engine.cancelStroke() else engine.endStroke()
                            strokeActive = false
                        }
                        mode = GestureMode.Transform
                    }

                    when (mode) {
                        GestureMode.Transform -> {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val focus = event.calculateCentroid(useCurrent = false)
                            if (focus.isSpecified && (zoom != 1f || pan != Offset.Zero)) currentOnTransform(zoom, focus, pan)
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                        GestureMode.Draw -> {
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed && event.motionEvent?.wasCanceled() == true) {
                                // The system decided this touch was a palm: drop the stroke rather than keep it.
                                engine.cancelStroke()
                                strokeActive = false
                                break
                            }
                            if (change.positionChanged()) {
                                travel += (change.position - change.previousPosition).getDistance()
                                engine.extendStroke(change.strokePoints(currentViewport.value ?: view))
                            }
                            change.consume()
                            if (!change.pressed) {
                                engine.endStroke()
                                strokeActive = false
                                break
                            }
                        }
                        GestureMode.Tap -> {
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                if (event.motionEvent?.wasCanceled() != true) {
                                    currentOnTap(view.toDocumentX(down.position.x), view.toDocumentY(down.position.y))
                                }
                                break
                            }
                        }
                    }
                }
            } finally {
                // The gesture was interrupted (e.g. the surface left the screen).
                if (strokeActive) engine.cancelStroke()
                strokeActive = false
                currentOnActiveChange(false)
            }
        }
    }

    Canvas(modifier.onSizeChanged { onViewSize(it.width.toFloat(), it.height.toFloat()) }.then(input)) {
        frameVersion.value
        val view = currentViewport.value ?: return@Canvas
        withTransform({
            translate(view.offsetX, view.offsetY)
            scale(view.zoom, view.zoom, pivot = Offset.Zero)
        }) {
            drawIntoCanvas { engine.draw(it.nativeCanvas) }
        }
        val dirty = engine.consumeDirtyRect()
        onFrameDrawn(dirty.width() * dirty.height() / documentPixels)
    }
}

private enum class GestureMode { Draw, Tap, Transform }

/** API 33+: the lifted pointer was accidental, e.g. a resting palm, and its gesture should be undone. */
internal fun MotionEvent.wasCanceled(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (flags and MotionEvent.FLAG_CANCELED) != 0

private fun PointerInputChange.toStrokePoint(view: Viewport) = StrokePoint(
    x = view.toDocumentX(position.x),
    y = view.toDocumentY(position.y),
    timeMs = uptimeMillis,
    pressure = pressure,
)

/** The MotionEvent's historical points since the last event, then the current one. */
private fun PointerInputChange.strokePoints(view: Viewport): List<StrokePoint> =
    historical.map {
        StrokePoint(view.toDocumentX(it.position.x), view.toDocumentY(it.position.y), it.uptimeMillis, pressure)
    } + toStrokePoint(view)
