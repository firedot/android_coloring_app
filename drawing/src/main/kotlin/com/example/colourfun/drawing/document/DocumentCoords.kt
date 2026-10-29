package com.example.colourfun.drawing.document

import kotlin.math.min

/**
 * Fit-centre mapping from document to view: `view = document * scale + (left, top)`.
 * [toDocumentX]/[toDocumentY] apply the inverse, for touch input.
 */
data class DocumentCoords(val scale: Float, val left: Float, val top: Float) {

    fun toDocumentX(viewX: Float): Float = (viewX - left) / scale

    fun toDocumentY(viewY: Float): Float = (viewY - top) / scale

    companion object {
        fun fit(viewWidth: Float, viewHeight: Float, size: DocumentSize): DocumentCoords {
            val scale = min(viewWidth / size.width, viewHeight / size.height)
            return DocumentCoords(
                scale = scale,
                left = (viewWidth - size.width * scale) / 2f,
                top = (viewHeight - size.height * scale) / 2f,
            )
        }
    }
}
