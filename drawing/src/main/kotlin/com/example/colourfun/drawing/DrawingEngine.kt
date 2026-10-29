package com.example.colourfun.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.example.colourfun.drawing.brushes.Brush
import com.example.colourfun.drawing.brushes.BrushSpec
import com.example.colourfun.drawing.brushes.StrokeContext
import com.example.colourfun.drawing.brushes.StrokePoint
import com.example.colourfun.drawing.document.Document
import com.example.colourfun.drawing.history.History
import com.example.colourfun.drawing.history.PixelPatch
import com.example.colourfun.drawing.history.StrokeCommand
import com.example.colourfun.drawing.stroke.Resampler
import com.example.colourfun.drawing.stroke.StrokeSmoother
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Draws strokes into a [Document] and records them in [history].
 *
 * Main thread only. Touch input comes straight from the drawing surface, not through a ViewModel.
 * Stroke pipeline: 1-euro smoothing → fixed-spacing resampling → brush draws into the stroke scratch
 * layer → on [endStroke] the scratch is merged into the paint layer and recorded as a [StrokeCommand].
 */
class DrawingEngine(val document: Document, private val history: History) {

    private val _frameVersion = MutableStateFlow(0L)

    /** Changes whenever the picture changes; the surface redraws when it does. */
    val frameVersion: StateFlow<Long> = _frameVersion.asStateFlow()

    val canUndo: StateFlow<Boolean> get() = history.canUndo
    val canRedo: StateFlow<Boolean> get() = history.canRedo

    private val documentRect = Rect(0, 0, document.size.width, document.size.height)
    private val dirty = Rect()
    private var stroke: ActiveStroke? = null

    private class ActiveStroke(
        val spec: BrushSpec,
        val color: Int,
        val brush: Brush,
        val context: StrokeContext,
        val resampler: Resampler,
    ) {
        val smoother = StrokeSmoother()
        val bounds = RectF()
        val mergePaint = Paint().also {
            it.alpha = (brush.opacity * 255).roundToInt()
            it.xfermode = PorterDuffXfermode(brush.blendMode)
        }
    }

    fun beginStroke(spec: BrushSpec, color: Int, p: StrokePoint) {
        cancelStroke()
        val width = spec.baseWidth * document.size.scale
        val s = ActiveStroke(
            spec = spec,
            color = color,
            brush = spec.createBrush(),
            context = StrokeContext(document.stroke.canvas, color, width),
            resampler = Resampler(spacing = (width / RESAMPLE_STEPS_PER_WIDTH).coerceAtLeast(1f)),
        )
        val start = s.smoother.filter(p)
        s.resampler.start(start)
        stroke = s
        include(s, s.brush.begin(s.context, start))
    }

    /** Adds input points in order, including the MotionEvent's historical points. */
    fun extendStroke(points: List<StrokePoint>) {
        val s = stroke ?: return
        val batch = arrayListOf(s.resampler.lastEmitted)
        for (p in points) s.resampler.add(s.smoother.filter(p), batch)
        drawBatch(s, batch)
    }

    fun endStroke() {
        val s = stroke ?: return
        val batch = arrayListOf(s.resampler.lastEmitted)
        s.resampler.finish(batch)
        drawBatch(s, batch)
        stroke = null

        val bounds = Rect().also { s.bounds.roundOut(it) }
        if (!bounds.intersect(documentRect)) return

        val paintBitmap = document.paint.bitmap
        val before = PixelPatch.capture(paintBitmap, bounds)
        document.paint.canvas.drawBitmap(document.stroke.bitmap, bounds, bounds, s.mergePaint)
        val after = PixelPatch.capture(paintBitmap, bounds)
        document.stroke.clear(bounds)
        history.push(StrokeCommand(bounds, before, after, s.spec, s.color))
        invalidate(bounds)
    }

    /** Discards the stroke in progress, e.g. when a second finger touches down. */
    fun cancelStroke() {
        val s = stroke ?: return
        stroke = null
        val bounds = Rect().also { s.bounds.roundOut(it) }
        document.stroke.clear(bounds)
        invalidate(bounds)
    }

    fun undo() {
        cancelStroke()
        history.undo(document.paint.bitmap)?.let(::invalidate)
    }

    fun redo() {
        cancelStroke()
        history.redo(document.paint.bitmap)?.let(::invalidate)
    }

    /** Draws the whole picture, including the stroke in progress, in document coordinates. */
    fun draw(canvas: Canvas) {
        document.draw(canvas, stroke?.mergePaint)
    }

    /** The area changed since the last call, in document pixels; empty if nothing changed. */
    fun consumeDirtyRect(): Rect {
        val result = Rect(dirty)
        dirty.setEmpty()
        return result
    }

    private fun drawBatch(s: ActiveStroke, batch: List<StrokePoint>) {
        if (batch.size < 2) return
        include(s, s.brush.segment(s.context, batch))
    }

    private fun include(s: ActiveStroke, rect: RectF) {
        s.bounds.union(rect)
        invalidate(Rect().also { rect.roundOut(it) })
    }

    private fun invalidate(rect: Rect) {
        val clipped = Rect(rect)
        if (clipped.intersect(documentRect)) dirty.union(clipped)
        _frameVersion.value++
    }

    private companion object {
        /** Resampled points per stroke width; dense enough that joins stay smooth. */
        const val RESAMPLE_STEPS_PER_WIDTH = 4f
    }
}
