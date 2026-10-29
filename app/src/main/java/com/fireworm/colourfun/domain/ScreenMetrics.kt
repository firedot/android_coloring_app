package com.fireworm.colourfun.domain

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The screen's size in physical pixels, including the area under the hidden system bars. Only its
 * aspect is used (to size new documents), so the orientation and display scaling don't matter.
 */
class ScreenMetrics @Inject constructor(@ApplicationContext private val context: Context) {

    fun size(): Pair<Int, Int> {
        val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
        return mode.physicalWidth to mode.physicalHeight
    }
}
