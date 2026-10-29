package com.fireworm.colourfun.drawing.viewport

import kotlin.math.min

/**
 * How the document is shown in a view of [viewWidth] × [viewHeight]: `screen = document × zoom +
 * offset`. [scale] 1 ("fit") shows the whole document centred; [MAX_SCALE] is the closest zoom.
 *
 * Immutable and pure Kotlin. Every result is clamped: the document always covers the view along an
 * axis where it is larger than the view, and is centred along an axis where it is smaller. Zoom is
 * view state only; it is never undoable and never saved.
 */
@ConsistentCopyVisibility
data class Viewport private constructor(
    val documentWidth: Int,
    val documentHeight: Int,
    val viewWidth: Float,
    val viewHeight: Float,
    /** Zoom relative to fit, in `1..MAX_SCALE`. */
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    /** Screen pixels per document pixel at fit. */
    val fitZoom: Float get() = min(viewWidth / documentWidth, viewHeight / documentHeight)

    /** Screen pixels per document pixel. */
    val zoom: Float get() = fitZoom * scale

    /** Shows the Fit button. */
    val isZoomedIn: Boolean get() = scale > ZOOMED_IN_SCALE

    fun toScreenX(documentX: Float): Float = documentX * zoom + offsetX
    fun toScreenY(documentY: Float): Float = documentY * zoom + offsetY
    fun toDocumentX(screenX: Float): Float = (screenX - offsetX) / zoom
    fun toDocumentY(screenY: Float): Float = (screenY - offsetY) / zoom

    /**
     * Zooms by [factor] around the screen point ([focusX], [focusY]), which keeps showing the same
     * document point, then moves by ([panX], [panY]) screen pixels. One pinch frame.
     */
    fun transform(factor: Float, focusX: Float, focusY: Float, panX: Float = 0f, panY: Float = 0f): Viewport {
        val newScale = (scale * factor).coerceIn(1f, MAX_SCALE)
        val docX = toDocumentX(focusX)
        val docY = toDocumentY(focusY)
        val newZoom = fitZoom * newScale
        return clamped(newScale, focusX - docX * newZoom + panX, focusY - docY * newZoom + panY)
    }

    fun pan(dx: Float, dy: Float): Viewport = clamped(scale, offsetX + dx, offsetY + dy)

    /** The whole document, centred. */
    fun fitted(): Viewport = clamped(1f, 0f, 0f)

    /** The document fitted into a view of the new size. */
    fun resized(viewWidth: Float, viewHeight: Float): Viewport = of(documentWidth, documentHeight, viewWidth, viewHeight)

    /** Interpolates scale and offset towards [target] for an animation; [t] in `0..1`. */
    fun lerp(target: Viewport, t: Float): Viewport =
        clamped(
            scale + (target.scale - scale) * t,
            offsetX + (target.offsetX - offsetX) * t,
            offsetY + (target.offsetY - offsetY) * t,
        )

    private fun clamped(newScale: Float, newOffsetX: Float, newOffsetY: Float): Viewport {
        val s = newScale.coerceIn(1f, MAX_SCALE)
        val z = fitZoom * s
        return copy(
            scale = s,
            offsetX = clampAxis(newOffsetX, documentWidth * z, viewWidth),
            offsetY = clampAxis(newOffsetY, documentHeight * z, viewHeight),
        )
    }

    companion object {
        const val MAX_SCALE = 4f

        /** Above this the view counts as zoomed in. */
        const val ZOOMED_IN_SCALE = 1.05f

        /** The document fitted into the view. */
        fun of(documentWidth: Int, documentHeight: Int, viewWidth: Float, viewHeight: Float): Viewport {
            require(documentWidth > 0 && documentHeight > 0) { "Document size must be positive" }
            require(viewWidth > 0f && viewHeight > 0f) { "View size must be positive" }
            return Viewport(documentWidth, documentHeight, viewWidth, viewHeight, 1f, 0f, 0f).fitted()
        }

        private fun clampAxis(offset: Float, content: Float, view: Float): Float =
            if (content <= view) (view - content) / 2f else offset.coerceIn(view - content, 0f)
    }
}
