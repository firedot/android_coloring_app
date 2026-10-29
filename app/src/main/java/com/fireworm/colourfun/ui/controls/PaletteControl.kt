package com.fireworm.colourfun.ui.controls

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R
import com.fireworm.colourfun.ui.icons.IconOutline
import com.fireworm.colourfun.ui.icons.paintSplatIcon
import com.fireworm.colourfun.ui.theme.Palette
import com.fireworm.colourfun.ui.theme.PaletteColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Diameter of the colour ring: 12 swatches around a circle with [PanelItemSpacing] between them. */
private val RingSize: Dp = run {
    val step = PanelItemSize + PanelItemSpacing
    // Chord between neighbours = step, so the radius is step / (2 sin(π / 12)).
    val radius = step / (2 * sin(PI / Palette.Wheel.size).toFloat())
    radius * 2 + PanelItemSize
}

/** The palette panel needs this much height to show the wheel as a ring; below it, a 4 × 3 grid. */
val PaletteRingMinHeight: Dp = RingSize + 24.dp

private const val GRID_COLUMNS = 4
private const val NEUTRAL_COLUMNS = 2

/**
 * The palette: a paint-splat button tinted with the current colour; its panel shows the 12-colour
 * wheel in wheel order (a ring, or a 4 × 3 grid when [ring] is false), then the neutrals, then the
 * pipette.
 */
@Composable
fun PaletteControl(
    color: Int,
    pipetteActive: Boolean,
    expanded: Boolean,
    ring: Boolean,
    onToggle: () -> Unit,
    onSelectColor: (Int) -> Unit,
    onSelectPipette: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpandableControl(
        expanded = expanded,
        onToggle = onToggle,
        label = stringResource(R.string.palette),
        alignment = Alignment.Start,
        modifier = modifier,
        icon = {
            val splat = remember(color) { paintSplatIcon(Color(color)) }
            Image(splat, contentDescription = null, modifier = Modifier.size(52.dp))
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing * 2), verticalAlignment = Alignment.CenterVertically) {
            if (ring) {
                ColorRing(Palette.Wheel, color, onSelectColor)
            } else {
                ColorGrid(Palette.Wheel, GRID_COLUMNS, color, onSelectColor)
            }
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(PanelItemSpacing),
            ) {
                // Neutrals, then the pipette in the last cell.
                val cells: List<PaletteColor?> = Palette.Neutrals + null
                cells.chunked(NEUTRAL_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing)) {
                        row.forEach { cell ->
                            if (cell != null) {
                                Swatch(cell, selected = cell.argb == color) { onSelectColor(cell.argb) }
                            } else {
                                PipetteButton(pipetteActive, onSelectPipette)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorGrid(colors: List<PaletteColor>, columns: Int, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PanelItemSpacing)) {
        colors.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing)) {
                row.forEach { Swatch(it, selected = it.argb == selected) { onSelect(it.argb) } }
            }
        }
    }
}

/** The colour wheel as a ring, red at the top, going clockwise. */
@Composable
private fun ColorRing(colors: List<PaletteColor>, selected: Int, onSelect: (Int) -> Unit) {
    Layout(
        modifier = Modifier.size(RingSize).selectableGroup(),
        content = { colors.forEach { Swatch(it, selected = it.argb == selected) { onSelect(it.argb) } } },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val side = RingSize.roundToPx()
        val item = PanelItemSize.roundToPx()
        val radius = (side - item) / 2f
        layout(side, side) {
            placeables.forEachIndexed { i, placeable ->
                val angle = 2 * PI * i / placeables.size - PI / 2
                val x = side / 2f + radius * cos(angle).toFloat() - item / 2f
                val y = side / 2f + radius * sin(angle).toFloat() - item / 2f
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

/** A round colour swatch; the selected one gets a thick ring. Light colours keep a thin outline. */
@Composable
private fun Swatch(color: PaletteColor, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(color.name)
    Box(
        Modifier
            .size(PanelItemSize)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .border(
                width = if (selected) 5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else IconOutline.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .padding(if (selected) 8.dp else 0.dp),
    ) {
        Box(Modifier.matchParentSize().clip(CircleShape).background(Color(color.argb)))
    }
}

@Composable
private fun PipetteButton(active: Boolean, onClick: () -> Unit) {
    FilledIconToggleButton(checked = active, onCheckedChange = { onClick() }, modifier = Modifier.size(PanelItemSize)) {
        Icon(painterResource(R.drawable.ic_pipette), contentDescription = stringResource(R.string.tool_pipette), modifier = Modifier.size(28.dp))
    }
}
