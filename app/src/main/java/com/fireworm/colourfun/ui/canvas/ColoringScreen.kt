package com.fireworm.colourfun.ui.canvas

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconToggleButton
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
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.ui.palette.SpectrumPalette

private val Letterbox = Color(0xFFE8E8E8)
private val ToolSpacing = 12.dp

@Composable
fun ColoringScreen(viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val frameTimeLogger = rememberFrameTimeLogger()

    val canvas = @Composable { modifier: Modifier ->
        Box(modifier) {
            engine?.let { engine: DrawingEngine ->
                DrawingSurface(
                    engine = engine,
                    tool = uiState.tool,
                    color = uiState.color,
                    stayInsideLines = uiState.stayInsideLines,
                    onTap = { x, y -> viewModel.onEvent(ColoringEvent.TapAt(x, y)) },
                    modifier = Modifier.fillMaxSize(),
                    onStrokeActiveChange = { active -> frameTimeLogger?.setActive(active) },
                    onFrameDrawn = { dirty -> frameTimeLogger?.onFrameDrawn(dirty) },
                )
            }
        }
    }
    val palette = @Composable { vertical: Boolean, modifier: Modifier ->
        SpectrumPalette(
            selected = uiState.color,
            onSelect = { viewModel.onEvent(ColoringEvent.SelectColor(it)) },
            vertical = vertical,
            modifier = modifier,
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Letterbox) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // The picture is 4:3, so the palette and tools go along the long edges of the screen.
            if (maxWidth >= maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    palette(true, Modifier.fillMaxHeight().padding(8.dp))
                    canvas(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(ToolSpacing, Alignment.CenterVertically),
                    ) {
                        ToolButtons(uiState, viewModel::onEvent)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(ToolSpacing, Alignment.CenterHorizontally),
                    ) {
                        ToolButtons(uiState, viewModel::onEvent)
                    }
                    canvas(Modifier.weight(1f).fillMaxWidth())
                    palette(false, Modifier.fillMaxWidth().padding(8.dp))
                }
            }
        }
    }
}

/** Temporary until the real tool bar exists. Emits the buttons into the caller's row or column. */
@Composable
private fun ToolButtons(uiState: ColoringUiState, onEvent: (ColoringEvent) -> Unit) {
    // Shows the current tool; tapping switches between pen and fill.
    val filling = uiState.tool == Tool.Fill
    ControlButton(
        icon = if (filling) R.drawable.ic_fill else R.drawable.ic_pen,
        label = if (filling) R.string.tool_fill else R.string.tool_pen,
    ) {
        onEvent(ColoringEvent.SelectTool(if (filling) Tool.Pen else Tool.Fill))
    }
    FilledIconToggleButton(
        checked = uiState.tool == Tool.Pipette,
        onCheckedChange = { onEvent(ColoringEvent.SelectTool(Tool.Pipette)) },
        modifier = Modifier.size(64.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_pipette),
            contentDescription = stringResource(R.string.tool_pipette),
            modifier = Modifier.size(32.dp),
        )
    }
    ControlButton(R.drawable.ic_undo, R.string.undo, enabled = uiState.canUndo) {
        onEvent(ColoringEvent.Undo)
    }
    ControlButton(R.drawable.ic_redo, R.string.redo, enabled = uiState.canRedo) {
        onEvent(ColoringEvent.Redo)
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
