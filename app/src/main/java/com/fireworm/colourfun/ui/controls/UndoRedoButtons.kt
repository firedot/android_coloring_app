package com.fireworm.colourfun.ui.controls

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R

/** Primary floating buttons that don't open a panel: home, undo, redo. */
val ActionButtonSize = 64.dp

/** Undo and redo, side by side; each is dimmed when there is nothing to undo or redo. */
@Composable
fun UndoRedoButtons(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing * 2)) {
        ActionButton(R.drawable.ic_undo, R.string.undo, canUndo, onUndo)
        ActionButton(R.drawable.ic_redo, R.string.redo, canRedo, onRedo)
    }
}

/** Back to the picture library. Always available, so the child is never stuck. */
@Composable
fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ActionButton(R.drawable.ic_home, R.string.home, enabled = true, onClick = onClick, modifier = modifier)
}

@Composable
private fun ActionButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(label)
    ControlButton(
        onClick = onClick,
        enabled = enabled,
        size = ActionButtonSize,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp).alpha(if (enabled) 1f else 0.35f),
            tint = LocalContentColor.current,
        )
    }
}
