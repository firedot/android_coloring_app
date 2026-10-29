package com.fireworm.colourfun.ui.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R

/** The floating buttons that open a panel. */
val ControlButtonSize = 72.dp

/** Panel items: big enough for small fingers, with room between them. */
val PanelItemSize = 56.dp
val PanelItemSpacing = 8.dp

private val PanelPadding = 12.dp
private val PanelShape = RoundedCornerShape(28.dp)

/** About 200 ms with a slight settle. */
private val PanelSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * A round floating button with a panel that expands above it. The button toggles the panel; the
 * caller collapses it when a choice is made or the canvas is tapped, and keeps only one open.
 *
 * @param alignment which edge the panel lines up with: [Alignment.Start] for a button at the
 * bottom-start corner, [Alignment.End] for one at the bottom-end corner.
 * @param icon the button's content, drawn inside a 72 dp circle.
 */
@Composable
fun ExpandableControl(
    expanded: Boolean,
    onToggle: () -> Unit,
    label: String,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(PanelItemSpacing),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(PanelSpring, expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(PanelSpring, shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Surface(shape = PanelShape, tonalElevation = 2.dp, shadowElevation = 6.dp) {
                Box(Modifier.padding(PanelPadding)) { panel() }
            }
        }
        val state = stringResource(if (expanded) R.string.panel_open else R.string.panel_closed)
        ControlButton(
            onClick = onToggle,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = state
                role = Role.Button
            },
            content = icon,
        )
    }
}

/** A round, raised floating button over the canvas. */
@Composable
fun ControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ControlButtonSize,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
