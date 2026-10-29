package com.fireworm.colourfun.ui.canvas

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import com.fireworm.colourfun.ui.tools.SizePicker
import com.fireworm.colourfun.ui.tools.ToolBar

private val Letterbox = Color(0xFFE8E8E8)
private val EdgePadding = 8.dp
private val HistoryButtonSize = 56.dp

@Composable
fun ColoringScreen(viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val frameTimeLogger = rememberFrameTimeLogger()
    val onEvent = viewModel::onEvent

    val canvas = @Composable { modifier: Modifier ->
        Box(modifier) {
            engine?.let { engine: DrawingEngine ->
                DrawingSurface(
                    engine = engine,
                    tool = uiState.tool,
                    color = uiState.color,
                    stayInsideLines = uiState.stayInsideLines,
                    onTap = { x, y -> onEvent(ColoringEvent.TapAt(x, y)) },
                    modifier = Modifier.fillMaxSize(),
                    onStrokeActiveChange = { active -> frameTimeLogger?.setActive(active) },
                    onFrameDrawn = { dirty -> frameTimeLogger?.onFrameDrawn(dirty) },
                )
            }
            if (uiState.loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(64.dp))
        }
    }
    val palette = @Composable { vertical: Boolean, modifier: Modifier ->
        SpectrumPalette(
            selected = uiState.color,
            onSelect = { onEvent(ColoringEvent.SelectColor(it)) },
            vertical = vertical,
            modifier = modifier,
        )
    }
    val tools = @Composable { vertical: Boolean ->
        ToolBar(
            tool = uiState.tool,
            size = uiState.size,
            onSelectTool = { onEvent(ColoringEvent.SelectTool(it)) },
            vertical = vertical,
        )
    }
    val sizes = @Composable {
        SizePicker(
            size = uiState.size,
            color = uiState.color,
            enabled = uiState.tool.strokeSpec != null,
            onSelect = { onEvent(ColoringEvent.SelectSize(it)) },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Letterbox) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // The picture is 4:3, so the palette and tools go along the long edges of the screen.
            if (maxWidth >= maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(EdgePadding),
                        verticalArrangement = Arrangement.spacedBy(EdgePadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NewAnimalButton(Modifier.size(width = 120.dp, height = 64.dp), enabled = !uiState.loading) {
                            onEvent(ColoringEvent.NewAnimal)
                        }
                        palette(true, Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(EdgePadding)) {
                            HistoryButtons(uiState, onEvent)
                        }
                    }
                    canvas(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(EdgePadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        tools(true)
                        sizes()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(EdgePadding),
                        verticalArrangement = Arrangement.spacedBy(EdgePadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        tools(false)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(EdgePadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            sizes()
                            Spacer(Modifier.weight(1f))
                            NewAnimalButton(Modifier.size(width = 88.dp, height = 56.dp), enabled = !uiState.loading) {
                                onEvent(ColoringEvent.NewAnimal)
                            }
                            HistoryButtons(uiState, onEvent)
                        }
                    }
                    canvas(Modifier.weight(1f).fillMaxWidth())
                    palette(false, Modifier.fillMaxWidth().padding(EdgePadding))
                }
            }
        }
    }
}

/** The big button that starts a new picture with a freshly generated animal. */
@Composable
private fun NewAnimalButton(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = CircleShape) {
        Icon(painterResource(R.drawable.ic_paw), contentDescription = stringResource(R.string.new_animal), modifier = Modifier.size(36.dp))
    }
}

/** Undo and redo; emitted into the caller's row. */
@Composable
private fun HistoryButtons(uiState: ColoringUiState, onEvent: (ColoringEvent) -> Unit) {
    ControlButton(R.drawable.ic_undo, R.string.undo, enabled = uiState.canUndo) { onEvent(ColoringEvent.Undo) }
    ControlButton(R.drawable.ic_redo, R.string.redo, enabled = uiState.canRedo) { onEvent(ColoringEvent.Redo) }
}

@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(HistoryButtonSize)) {
        Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(28.dp))
    }
}
