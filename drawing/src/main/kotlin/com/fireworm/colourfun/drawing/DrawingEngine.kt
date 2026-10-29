package com.fireworm.colourfun.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.fireworm.colourfun.drawing.brushes.Brush
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.StrokeContext
import com.fireworm.colourfun.drawing.brushes.StrokePoint
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.history.FillCommand
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.history.PixelPatch
import com.fireworm.colourfun.drawing.history.StrokeCommand
import com.fireworm.colourfun.drawing.pipette.ColorSampler
import com.fireworm.colourfun.drawing.regions.RegionMap
import com.fireworm.colourfun.drawing.regions.RegionMaskCache
import com.fireworm.colourfun.drawing.stroke.Resampler
import com.fireworm.colourfun.drawing.stroke.StrokeSmoother
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Draws strokes and fills into a [Document] and records them in [history].
 *
 * Main thread only. Touch input comes straight from the drawing surface, not through a ViewModel.
 * Stroke pipeline: 1-euro smoothing → fixed-spacing resampling → brush draws into the stroke scratch
 * layer → (stay inside the lines) clipped to the starting region's mask → on [endStroke] the scratch
 * is merged into the paint layer and recorded as a [StrokeCommand].
 */
class DrawingEngine(
    val document: Document,
    private val masks: RegionMaskCache,
    private val history: History,
) {
    private val _frameVersion = MutableStateFlow(0L)

    /** Changes whenever the picture changes; the surface redraws when it does. */
    val frameVersion: StateFlow<Long> = _frameVersion.asStateFlow()

    val canUndo: StateFlow<Boolean> get() = history.canUndo
    val canRedo: StateFlow<Boolean> get() = history.canRedo

    val regions: RegionMap get() = masks.regions

    private val sampler = ColorSampler(document.paint.bitmap, document.paper.color, masks.regions)

    private val documentRect = Rect(0, 0, document.size.width, document.size.height)
    private val dirty = Rect()
    private var stroke: ActiveStroke? = null

    private val clipPaint = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val eraseInMaskPaint = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val fillPaint = Paint()

    private class ActiveStroke(
        val spec: BrushSpec,
        val color: Int,
        val brush: Brush,
        val context: StrokeContext,
        val resampler: Resampler,
        /** The starting region's mask when staying inside the lines, else `null`. */
        val mask: Bitmap?,
    ) {
        val smoother = StrokeSmoother()
        val bounds = RectF()
        val mergePaint = Paint().also {
            it.alpha = (brush.opacity * 255).roundToInt()
            it.xfermode = PorterDuffXfermode(brush.blendMode)
        }
    }

    /**
     * @param stayInsideLines keep the stroke inside the region under [p]; on the outline, the
     * nearest region.
     */
    fun beginStroke(spec: BrushSpec, color: Int, p: StrokePoint, stayInsideLines: Boolean) {
        cancelStroke()
        val width = spec.baseWidth * document.size.scale
        val regionId = if (stayInsideLines) regions.regionNear(p.x, p.y) else 0
        val s = ActiveStroke(
            spec = spec,
            color = color,
            brush = spec.createBrush(),
            context = StrokeContext(document.stroke.canvas, color, width),
            resampler = Resampler(spacing = (width / RESAMPLE_STEPS_PER_WIDTH).coerceAtLeast(1f)),
            mask = if (regionId != 0) masks.get(regionId) else null,
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

    /**
     * Paints every pixel of the dilated region at ([x], [y]) with [color], replacing what is there.
     * On the outline it fills the nearest region. The mask is built off the main thread; it is
     * applied to the paint as it is when the mask is ready, so nothing drawn meanwhile is lost.
     */
    suspend fun fill(x: Float, y: Float, color: Int) {
        val regionId = regions.regionNear(x, y)
        if (regionId == 0) return
        val mask = masks.load(regionId)

        val bounds = Rect(regions.region(regionId).maskBounds)
        val paintBitmap = document.paint.bitmap
        val before = PixelPatch.capture(paintBitmap, bounds)
        // Clear, then paint: an exact replace through a 0/255 mask, for any colour.
        document.paint.canvas.drawBitmap(mask, bounds, bounds, eraseInMaskPaint)
        fillPaint.color = color
        document.paint.canvas.drawBitmap(mask, bounds, bounds, fillPaint)
        val after = PixelPatch.capture(paintBitmap, bounds)
        history.push(FillCommand(bounds, before, after, regionId, color))
        invalidate(bounds)
    }

    /** The colour the child sees at a document point, for the pipette. See [ColorSampler]. */
    fun sampleColor(x: Float, y: Float): Int = sampler.sample(x, y)

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
        val changed = Rect().also { rect.roundOut(it) }
        val mask = s.mask
        if (mask != null) {
            val clip = Rect(changed)
            if (clip.intersect(documentRect)) document.stroke.canvas.drawBitmap(mask, clip, clip, clipPaint)
        }
        s.bounds.union(rect)
        invalidate(changed)
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
