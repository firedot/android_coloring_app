package com.fireworm.colourfun.ui.canvas

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireworm.colourfun.R
import com.fireworm.colourfun.debug.rememberFrameTimeLogger

private val Letterbox = Color(0xFFE8E8E8)

@Composable
fun ColoringScreen(viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val frameTimeLogger = rememberFrameTimeLogger()

    Surface(modifier = Modifier.fillMaxSize(), color = Letterbox) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                engine?.let {
                    DrawingSurface(
                        engine = it,
                        tool = uiState.tool,
                        color = uiState.color,
                        stayInsideLines = uiState.stayInsideLines,
                        onFill = { x, y -> viewModel.onEvent(ColoringEvent.FillAt(x, y)) },
                        modifier = Modifier.fillMaxSize(),
                        onStrokeActiveChange = { active -> frameTimeLogger?.setActive(active) },
                        onFrameDrawn = { dirty -> frameTimeLogger?.onFrameDrawn(dirty) },
                    )
                }
            }
            // Temporary until the real tool bar exists.
            Column(
                modifier = Modifier.fillMaxHeight().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                // Shows the current tool; tapping switches between pen and fill.
                val filling = uiState.tool == Tool.Fill
                ControlButton(
                    icon = if (filling) R.drawable.ic_fill else R.drawable.ic_pen,
                    label = if (filling) R.string.tool_fill else R.string.tool_pen,
                ) {
                    viewModel.onEvent(ColoringEvent.SelectTool(if (filling) Tool.Pen else Tool.Fill))
                }
                ControlButton(R.drawable.ic_undo, R.string.undo, enabled = uiState.canUndo) {
                    viewModel.onEvent(ColoringEvent.Undo)
                }
                ControlButton(R.drawable.ic_redo, R.string.redo, enabled = uiState.canRedo) {
                    viewModel.onEvent(ColoringEvent.Redo)
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(32.dp))
    }
}
