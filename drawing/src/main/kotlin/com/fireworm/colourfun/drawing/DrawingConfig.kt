package com.fireworm.colourfun.drawing

import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.document.DocumentSizer
import com.fireworm.colourfun.drawing.history.History

/** Device-dependent settings for the drawing engine. */
data class DrawingConfig(val isLowRamDevice: Boolean, val historyBudgetBytes: Long) {

    /** The size of a new document on a screen of this size. */
    fun documentSize(screenWidth: Int, screenHeight: Int): DocumentSize =
        DocumentSizer.forScreen(screenWidth, screenHeight, isLowRamDevice)

    companion object {
        fun forDevice(isLowRamDevice: Boolean) = DrawingConfig(
            isLowRamDevice = isLowRamDevice,
            historyBudgetBytes = if (isLowRamDevice) History.LOW_RAM_BUDGET_BYTES else History.DEFAULT_BUDGET_BYTES,
        )
    }
}
