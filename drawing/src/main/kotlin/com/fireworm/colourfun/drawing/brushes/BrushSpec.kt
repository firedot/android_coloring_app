package com.fireworm.colourfun.drawing.brushes

enum class Medium { Pencil, Pen, Chalk, Spray, Brush, PaintBrush, MagicPen, Eraser }

/** Preset sizes instead of a slider, so small hands get it right. Resolved per medium. */
enum class BrushSize { Small, Medium, Large }

data class BrushSpec(val medium: Medium, val size: BrushSize) {

    /** Stroke width in base (2048 px wide) document pixels. */
    val baseWidth: Float
        get() = when (medium) {
            Medium.Pencil -> sized(5f, 10f, 18f)
            Medium.Pen -> sized(8f, 16f, 28f)
            Medium.Chalk -> sized(14f, 26f, 44f)
            Medium.Spray -> sized(32f, 56f, 96f)
            Medium.Brush -> sized(12f, 24f, 40f)
            Medium.PaintBrush -> sized(20f, 36f, 60f)
            Medium.MagicPen -> sized(10f, 16f, 26f)
            Medium.Eraser -> sized(16f, 32f, 60f)
        }

    fun createBrush(): Brush = when (medium) {
        Medium.Pencil -> Pencil()
        Medium.Pen -> Pen()
        Medium.Chalk -> Chalk()
        Medium.Spray -> Spray()
        Medium.Brush -> InkBrush()
        Medium.PaintBrush -> PaintBrush()
        Medium.MagicPen -> MagicPen()
        Medium.Eraser -> Eraser()
    }

    private fun sized(small: Float, medium: Float, large: Float) = when (size) {
        BrushSize.Small -> small
        BrushSize.Medium -> medium
        BrushSize.Large -> large
    }
}
