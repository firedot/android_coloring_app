package com.fireworm.colourfun.ui.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R

private val FitButtonSize = 64.dp

/** Returns to the whole picture. Only shown while zoomed in; there is no double-tap zoom. */
@Composable
fun FitButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(FitButtonSize), shape = CircleShape) {
            Icon(painterResource(R.drawable.ic_fit), contentDescription = stringResource(R.string.fit), modifier = Modifier.size(32.dp))
        }
    }
}
