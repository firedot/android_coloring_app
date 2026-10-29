package com.fireworm.colourfun.drawing.history

import android.graphics.Rect
import com.fireworm.colourfun.drawing.brushes.BrushSpec

/** An undoable change to the paint layer: its pixels inside [bounds] before and after. */
sealed interface Command {
    val bounds: Rect
    val before: PixelPatch
    val after: PixelPatch

    val compressedBytes: Long get() = before.compressedBytes.toLong() + after.compressedBytes
}

class StrokeCommand(
    override val bounds: Rect,
    override val before: PixelPatch,
    override val after: PixelPatch,
    val spec: BrushSpec,
    val color: Int,
    /** The brush randomness seed, so the stroke can be rendered again identically. */
    val seed: Long,
) : Command

class FillCommand(
    override val bounds: Rect,
    override val before: PixelPatch,
    override val after: PixelPatch,
    val region: Int,
    val color: Int,
) : Command
