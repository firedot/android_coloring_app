package com.fireworm.colourfun.drawing.document

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sizes a new document to the screen so the canvas fills it without stretching: the height is
 * fixed and the width follows the screen aspect, within 4:3 to 2.25:1. Screens outside that range
 * are letterboxed by the view. Saved artworks keep the size they were made at. Pure Kotlin.
 */
object DocumentSizer {
    const val MAX_WIDTH = 3456
    const val LOW_RAM_SCALE = 0.75f

    /**
     * The app is landscape only, so the aspect is the long side over the short side: a document
     * started while a tablet is held upright still fills the screen in landscape.
     */
    fun forScreen(screenWidth: Int, screenHeight: Int, isLowRamDevice: Boolean): DocumentSize {
        require(screenWidth > 0 && screenHeight > 0) { "Screen size must be positive" }
        val aspect = max(screenWidth, screenHeight) / min(screenWidth, screenHeight).toFloat()
        val scale = if (isLowRamDevice) LOW_RAM_SCALE else 1f
        val height = (DocumentSize.PICTURE_HEIGHT * scale).roundToInt()
        val minWidth = (DocumentSize.PICTURE_WIDTH * scale).roundToInt()
        val maxWidth = (MAX_WIDTH * scale).roundToInt()
        val width = (height * aspect).roundToInt().coerceIn(minWidth, maxWidth)
        return DocumentSize(width, height, scale)
    }
}
