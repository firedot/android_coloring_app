package com.fireworm.colourfun.ui.canvas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireworm.colourfun.R
import com.fireworm.colourfun.debug.rememberFrameTimeLogger
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.ui.feedback.Sound
import com.fireworm.colourfun.ui.feedback.rememberFeedback
import com.fireworm.colourfun.ui.gate.ParentGateDialog
import com.fireworm.colourfun.ui.palette.SpectrumPalette
import com.fireworm.colourfun.ui.tools.SizePicker
import com.fireworm.colourfun.ui.tools.ToolBar

private val Letterbox = Color(0xFFE8E8E8)
private val EdgePadding = 8.dp
private val HistoryButtonSize = 56.dp

@Composable
fun ColoringScreen(onOpenGallery: () -> Unit, onOpenSettings: () -> Unit, viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val frameTimeLogger = rememberFrameTimeLogger()
    val feedback = rememberFeedback()
    val onEvent: (ColoringEvent) -> Unit = { event ->
        soundFor(event, uiState.tool)?.let(feedback)
        viewModel.onEvent(event)
    }

    // Leaving the screen or the app: save without waiting for the autosave delay.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onEvent(ColoringEvent.Save) }

    val snackbar = remember { SnackbarHostState() }
    val exportDone = stringResource(R.string.export_done)
    val exportFailed = stringResource(R.string.export_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            snackbar.showSnackbar(if (effect == ColoringEffect.Exported) exportDone else exportFailed)
        }
    }

    // Saving to photos on API 26–28 needs the storage permission, asked for only after the gate.
    val context = LocalContext.current
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onEvent(ColoringEvent.ExportToGallery)
    }
    fun export() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else onEvent(ColoringEvent.ExportToGallery)
    }

    var gate by rememberSaveable { mutableStateOf<GateTarget?>(null) }
    gate?.let { target ->
        ParentGateDialog(
            onPass = {
                gate = null
                when (target) {
                    GateTarget.Settings -> onOpenSettings()
                    GateTarget.Export -> export()
                }
            },
            onDismiss = { gate = null },
        )
    }
    val parentButtons = @Composable {
        ParentButton(R.drawable.ic_download, R.string.export) {
            feedback(Sound.Tap)
            gate = GateTarget.Export
        }
        ParentButton(R.drawable.ic_settings, R.string.settings) {
            feedback(Sound.Tap)
            gate = GateTarget.Settings
        }
    }

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
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
                        GalleryButton(Modifier.size(width = 120.dp, height = HistoryButtonSize)) {
                            feedback(Sound.Tap)
                            onOpenGallery()
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
                        Row(horizontalArrangement = Arrangement.spacedBy(EdgePadding)) { parentButtons() }
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
                            HistoryButtons(uiState, onEvent)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(EdgePadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GalleryButton(Modifier.size(width = 72.dp, height = HistoryButtonSize)) {
                                feedback(Sound.Tap)
                                onOpenGallery()
                            }
                            NewAnimalButton(Modifier.size(width = 112.dp, height = 64.dp), enabled = !uiState.loading) {
                                onEvent(ColoringEvent.NewAnimal)
                            }
                            Spacer(Modifier.weight(1f))
                            parentButtons()
                        }
                    }
                    canvas(Modifier.weight(1f).fillMaxWidth())
                    palette(false, Modifier.fillMaxWidth().padding(EdgePadding))
                }
            }
        }
    }
}

/** The sound for a child's action, or `null` for events that are not taps. */
private fun soundFor(event: ColoringEvent, tool: Tool): Sound? = when (event) {
    is ColoringEvent.SelectTool, is ColoringEvent.SelectSize, ColoringEvent.Undo, ColoringEvent.Redo, ColoringEvent.NewAnimal -> Sound.Tap
    is ColoringEvent.SelectColor -> Sound.Tick
    is ColoringEvent.TapAt -> if (tool == Tool.Fill) Sound.Fill else Sound.Tap
    ColoringEvent.ToggleStayInsideLines, ColoringEvent.Save, ColoringEvent.ExportToGallery -> null
}

/** What the parent gate is guarding. */
private enum class GateTarget { Settings, Export }

/** Small and plain: for grown-ups, behind the parent gate. */
@Composable
private fun ParentButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(label), tint = Color.Gray)
    }
}

/** The big button that starts a new picture with a freshly generated animal. */
@Composable
private fun NewAnimalButton(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = CircleShape) {
        Icon(painterResource(R.drawable.ic_paw), contentDescription = stringResource(R.string.new_animal), modifier = Modifier.size(36.dp))
    }
}

/** Opens the saved pictures. */
@Composable
private fun GalleryButton(modifier: Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier, shape = CircleShape) {
        Icon(painterResource(R.drawable.ic_gallery), contentDescription = stringResource(R.string.gallery), modifier = Modifier.size(28.dp))
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
