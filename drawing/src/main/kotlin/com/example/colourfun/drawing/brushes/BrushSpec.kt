package com.example.colourfun.drawing.brushes

enum class Medium { Pen }

/** Preset sizes instead of a slider, so small hands get it right. */
enum class BrushSize { Small, Medium, Large }

data class BrushSpec(val medium: Medium, val size: BrushSize) {

    /** Stroke width in base (2048 px wide) document pixels. */
    val baseWidth: Float
        get() = when (medium) {
            Medium.Pen -> when (size) {
                BrushSize.Small -> 8f
                BrushSize.Medium -> 16f
                BrushSize.Large -> 28f
            }
        }

    fun createBrush(): Brush = when (medium) {
        Medium.Pen -> Pen()
    }
}
