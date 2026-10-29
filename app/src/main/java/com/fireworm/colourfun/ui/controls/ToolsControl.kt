package com.fireworm.colourfun.ui.controls

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.fireworm.colourfun.ui.canvas.strokeSpec
import com.fireworm.colourfun.ui.icons.IconOutline
import com.fireworm.colourfun.ui.icons.paintBrushIcon

/** Tools are primary controls. */
private val ToolButtonSize = 64.dp
private const val TOOL_COLUMNS = 5

private class ToolItem(val tool: Tool, @DrawableRes val icon: Int, @StringRes val label: Int, val usesColor: Boolean)

/** The tools panel in order: the drawing mediums, then the eraser and the fill bucket. */
private fun toolItems(size: BrushSize): List<ToolItem> = listOf(
    ToolItem(Tool.Draw(BrushSpec(Medium.Pencil, size)), R.drawable.ic_pencil, R.string.tool_pencil, usesColor = true),
    ToolItem(Tool.Draw(BrushSpec(Medium.Pen, size)), R.drawable.ic_pen, R.string.tool_pen, usesColor = true),
    ToolItem(Tool.Draw(BrushSpec(Medium.Chalk, size)), R.drawable.ic_chalk, R.string.tool_chalk, usesColor = true),
    ToolItem(Tool.Draw(BrushSpec(Medium.Spray, size)), R.drawable.ic_spray, R.string.tool_spray, usesColor = true),
    ToolItem(Tool.Draw(BrushSpec(Medium.Brush, size)), R.drawable.ic_brush, R.string.tool_brush, usesColor = true),
    ToolItem(Tool.Draw(BrushSpec(Medium.PaintBrush, size)), R.drawable.ic_paint_brush, R.string.tool_paint_brush, usesColor = true),
    ToolItem(Tool.Draw(BrushSpec(Medium.MagicPen, size)), R.drawable.ic_magic_pen, R.string.tool_magic_pen, usesColor = true),
    ToolItem(Tool.Erase(size), R.drawable.ic_eraser, R.string.tool_eraser, usesColor = false),
    ToolItem(Tool.Fill, R.drawable.ic_fill, R.string.tool_fill, usesColor = true),
)

/** The glyph for a tool, also shown as the tools button's badge. */
@DrawableRes
fun Tool.icon(): Int = when (this) {
    is Tool.Draw -> when (spec.medium) {
        Medium.Pencil -> R.drawable.ic_pencil
        Medium.Pen -> R.drawable.ic_pen
        Medium.Chalk -> R.drawable.ic_chalk
        Medium.Spray -> R.drawable.ic_spray
        Medium.Brush -> R.drawable.ic_brush
        Medium.PaintBrush -> R.drawable.ic_paint_brush
        Medium.MagicPen -> R.drawable.ic_magic_pen
        Medium.Eraser -> R.drawable.ic_eraser
    }
    is Tool.Erase -> R.drawable.ic_eraser
    Tool.Fill -> R.drawable.ic_fill
    Tool.Pipette -> R.drawable.ic_pipette
}

/** The same medium, or the same kind of tool, ignoring the size. */
private fun Tool.sameKind(other: Tool): Boolean = when (this) {
    is Tool.Draw -> other is Tool.Draw && other.spec.medium == spec.medium
    is Tool.Erase -> other is Tool.Erase
    else -> this == other
}

/**
 * The tools: a paint-brush button whose tip shows the current colour, with a badge of the current
 * tool. Its panel lists every tool, then three size dots (S/M/L) for the drawing tools.
 */
@Composable
fun ToolsControl(
    tool: Tool,
    size: BrushSize,
    color: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectTool: (Tool) -> Unit,
    onSelectSize: (BrushSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpandableControl(
        expanded = expanded,
        onToggle = onToggle,
        label = stringResource(R.string.tools),
        alignment = Alignment.End,
        modifier = modifier,
        icon = {
            val brush = remember(color) { paintBrushIcon(Color(color)) }
            Box {
                Image(brush, contentDescription = null, modifier = Modifier.size(52.dp))
                Badge(tool.icon(), Modifier.align(Alignment.TopEnd))
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PanelItemSpacing * 2), horizontalAlignment = Alignment.End) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PanelItemSpacing)) {
                toolItems(size).chunked(TOOL_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing)) {
                        row.forEach { item ->
                            ToolButton(item, selected = item.tool.sameKind(tool), color = color) { onSelectTool(item.tool) }
                        }
                    }
                }
            }
            SizePicker(size = size, color = color, enabled = tool.strokeSpec != null, onSelect = onSelectSize)
        }
    }
}

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
        horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing),
    ) {
        BrushSize.entries.forEach { option ->
            val label = stringResource(option.label)
            FilledIconToggleButton(
                checked = option == size,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.size(PanelItemSize).semantics { contentDescription = label },
            ) {
                ColorDot(color, option.dot)
            }
        }
    }
}

@Composable
private fun ToolButton(item: ToolItem, selected: Boolean, color: Int, onClick: () -> Unit) {
    FilledIconToggleButton(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(ToolButtonSize)) {
        Box {
            Icon(painterResource(item.icon), contentDescription = stringResource(item.label), modifier = Modifier.size(32.dp))
            // The current colour, where the tool paints with it.
            if (item.usesColor) ColorDot(color, 14.dp, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp))
        }
    }
}

/** A small round badge with the current tool's glyph. */
@Composable
private fun Badge(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .border(1.dp, IconOutline.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** A dot in [color]; a thin outline keeps white and pale colours visible. */
@Composable
private fun ColorDot(color: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(Color(color), CircleShape)
            .border(1.dp, Color(0x40000000), CircleShape),
    )
}

private val BrushSize.dot: Dp
    get() = when (this) {
        BrushSize.Small -> 12.dp
        BrushSize.Medium -> 22.dp
        BrushSize.Large -> 34.dp
    }

@get:StringRes
private val BrushSize.label: Int
    get() = when (this) {
        BrushSize.Small -> R.string.size_small
        BrushSize.Medium -> R.string.size_medium
        BrushSize.Large -> R.string.size_large
    }
