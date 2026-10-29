package com.fireworm.colourfun.ui.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.ui.canvas.Tool
import kotlin.math.ceil

private val ToolButtonSize = 64.dp
private val SizeButtonSize = 48.dp
private val Spacing = 8.dp

/** Most rows the tool grid uses down a side edge, so it fits a landscape phone. */
private const val MAX_ROWS = 4
private const val ROW_COLUMNS = 5

private data class MediumItem(val medium: Medium, @DrawableRes val icon: Int, @StringRes val label: Int)

/** The drawing mediums in toolbar order; the eraser is its own tool. */
private val Mediums = listOf(
    MediumItem(Medium.Pencil, R.drawable.ic_pencil, R.string.tool_pencil),
    MediumItem(Medium.Pen, R.drawable.ic_pen, R.string.tool_pen),
    MediumItem(Medium.Chalk, R.drawable.ic_chalk, R.string.tool_chalk),
    MediumItem(Medium.Spray, R.drawable.ic_spray, R.string.tool_spray),
    MediumItem(Medium.Brush, R.drawable.ic_brush, R.string.tool_brush),
    MediumItem(Medium.PaintBrush, R.drawable.ic_paint_brush, R.string.tool_paint_brush),
    MediumItem(Medium.MagicPen, R.drawable.ic_magic_pen, R.string.tool_magic_pen),
)

/**
 * Every tool as a grid of 64 dp buttons: the drawing mediums, eraser, fill and pipette. The selected
 * one is filled in. Choosing a medium keeps the current [size].
 *
 * @param vertical laid out down a side edge (columns grow, at most [MAX_ROWS] rows); otherwise in
 * rows across the top.
 */
@Composable
fun ToolBar(
    tool: Tool,
    size: BrushSize,
    onSelectTool: (Tool) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedMedium = (tool as? Tool.Draw)?.spec?.medium
    val entries = Mediums.map { ToolEntry(it.icon, it.label, it.medium == selectedMedium, Tool.Draw(BrushSpec(it.medium, size))) } +
        ToolEntry(R.drawable.ic_eraser, R.string.tool_eraser, tool is Tool.Erase, Tool.Erase(size)) +
        ToolEntry(R.drawable.ic_fill, R.string.tool_fill, tool == Tool.Fill, Tool.Fill) +
        ToolEntry(R.drawable.ic_pipette, R.string.tool_pipette, tool == Tool.Pipette, Tool.Pipette)
    BoxWithConstraints(modifier) {
        // Across the top, as many full-size buttons per row as fit (narrow phones get more rows).
        val fitting = ((maxWidth + Spacing) / (ToolButtonSize + Spacing)).toInt().coerceIn(1, ROW_COLUMNS)
        val columns = if (vertical) ceil(entries.size / MAX_ROWS.toFloat()).toInt() else fitting
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                    row.forEach { entry -> ToolButton(entry.icon, entry.label, entry.selected) { onSelectTool(entry.tool) } }
                }
            }
        }
    }
}

private class ToolEntry(@DrawableRes val icon: Int, @StringRes val label: Int, val selected: Boolean, val tool: Tool)

/**
 * Three brush sizes as dots of increasing size in the current [color]; no slider. Dimmed when the
 * current tool doesn't stroke (fill, pipette).
 */
@Composable
fun SizePicker(
    size: BrushSize,
    color: Int,
    enabled: Boolean,
    onSelect: (BrushSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectableGroup().alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(Spacing),
    ) {
        BrushSize.entries.forEach { option ->
            val label = stringResource(option.label)
            FilledIconToggleButton(
                checked = option == size,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.size(SizeButtonSize).semantics { contentDescription = label },
            ) {
                Box(
                    Modifier
                        .size(option.dot)
                        .background(Color(color), CircleShape)
                        // Keeps white and pale colours visible on the button.
                        .border(1.dp, Color(0x40000000), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun ToolButton(@DrawableRes icon: Int, @StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    FilledIconToggleButton(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(ToolButtonSize)) {
        Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(32.dp))
    }
}

private val BrushSize.dot: Dp
    get() = when (this) {
        BrushSize.Small -> 10.dp
        BrushSize.Medium -> 18.dp
        BrushSize.Large -> 28.dp
    }

@get:StringRes
private val BrushSize.label: Int
    get() = when (this) {
        BrushSize.Small -> R.string.size_small
        BrushSize.Medium -> R.string.size_medium
        BrushSize.Large -> R.string.size_large
    }
