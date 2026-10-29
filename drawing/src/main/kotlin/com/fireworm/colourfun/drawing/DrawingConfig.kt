package com.fireworm.colourfun.drawing

import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.history.History

/** Device-dependent sizes for the drawing engine. */
data class DrawingConfig(val documentSize: DocumentSize, val historyBudgetBytes: Long) {
    companion object {
        fun forDevice(isLowRamDevice: Boolean) = DrawingConfig(
            documentSize = DocumentSize.forDevice(isLowRamDevice),
            historyBudgetBytes = if (isLowRamDevice) History.LOW_RAM_BUDGET_BYTES else History.DEFAULT_BUDGET_BYTES,
        )
    }
}
