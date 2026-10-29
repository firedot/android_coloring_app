package com.fireworm.colourfun.drawing.document

import kotlin.math.roundToInt

/**
 * The drawing document: [width] × [height] pixels with the picture area centred in it. All drawing
 * sizes are in these pixels. Pure Kotlin.
 *
 * @param scale of the picture relative to its 2048 × 1536 source: 1, or 0.75 on low-RAM devices.
 * Brush sizes and line widths are multiplied by it.
 */
data class DocumentSize(val width: Int, val height: Int, val scale: Float = 1f) {
    val pictureWidth: Int get() = (PICTURE_WIDTH * scale).roundToInt()
    val pictureHeight: Int get() = (PICTURE_HEIGHT * scale).roundToInt()

    /** Left edge of the picture area; the side margins are blank paper. */
    val pictureLeft: Int get() = (width - pictureWidth) / 2
    val pictureTop: Int get() = (height - pictureHeight) / 2

    companion object {
        /** Every library picture is exactly this size. */
        const val PICTURE_WIDTH = 2048
        const val PICTURE_HEIGHT = 1536
    }
}
