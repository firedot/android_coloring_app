package com.fireworm.colourfun.drawing.document

/** Size of the fixed logical drawing document. All drawing sizes are in these pixels. */
data class DocumentSize(val width: Int, val height: Int) {
    /** Scale relative to the 2048×1536 base document that line art is authored in. */
    val scale: Float get() = width / BASE_WIDTH.toFloat()

    companion object {
        const val BASE_WIDTH = 2048
        const val BASE_HEIGHT = 1536
        private const val LOW_RAM_SCALE = 0.75f

        val Standard = DocumentSize(BASE_WIDTH, BASE_HEIGHT)
        val LowRam = DocumentSize((BASE_WIDTH * LOW_RAM_SCALE).toInt(), (BASE_HEIGHT * LOW_RAM_SCALE).toInt())

        fun forDevice(isLowRamDevice: Boolean): DocumentSize = if (isLowRamDevice) LowRam else Standard
    }
}
