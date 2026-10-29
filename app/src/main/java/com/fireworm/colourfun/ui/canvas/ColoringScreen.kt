package com.fireworm.colourfun.ui.canvas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireworm.colourfun.R
import com.fireworm.colourfun.debug.rememberFrameTimeLogger
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.ui.controls.ActionButtonSize
import com.fireworm.colourfun.ui.controls.ControlButtonSize
import com.fireworm.colourfun.ui.controls.FitButton
import com.fireworm.colourfun.ui.controls.HomeButton
import com.fireworm.colourfun.ui.controls.PaletteControl
import com.fireworm.colourfun.ui.controls.PaletteRingMinHeight
import com.fireworm.colourfun.ui.controls.ToolsControl
import com.fireworm.colourfun.ui.controls.UndoRedoButtons
import com.fireworm.colourfun.ui.feedback.Sound
import com.fireworm.colourfun.ui.feedback.rememberFeedback
import com.fireworm.colourfun.ui.gate.ParentGateDialog

private val Letterbox = Color(0xFFE8E8E8)
private val EdgePadding = 12.dp

@Composable
fun ColoringScreen(onHome: () -> Unit, onOpenSettings: () -> Unit, viewModel: CanvasViewModel = hiltViewModel()) {
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
    // Back closes an open panel before it leaves the screen.
    BackHandler(enabled = uiState.openPanel != null) { onEvent(ColoringEvent.ClosePanels) }

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

    // The canvas fills the whole screen, under the system bars and cutouts; the controls float on
    // top, inset by the safe area. Letterboxed screens show the backdrop around the document.
    Surface(modifier = Modifier.fillMaxSize(), color = Letterbox) {
        Box(Modifier.fillMaxSize()) {
            val canvasDescription = stringResource(R.string.canvas)
            engine?.let { engine: DrawingEngine ->
                DrawingSurface(
                    engine = engine,
                    viewport = viewModel.viewport,
                    tool = uiState.tool,
                    color = uiState.color,
                    stayInsideLines = uiState.stayInsideLines,
                    onTap = { x, y -> onEvent(ColoringEvent.TapAt(x, y)) },
                    onViewSize = viewModel::onViewSize,
                    onTransform = { factor, focus, pan -> viewModel.transformViewport(factor, focus.x, focus.y, pan.x, pan.y) },
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = canvasDescription },
                    panelOpen = uiState.openPanel != null,
                    onDismissPanels = { onEvent(ColoringEvent.ClosePanels) },
                    // Measures frame time (texture upload) while drawing and while zooming.
                    onActiveChange = { active -> frameTimeLogger?.setActive(active) },
                    onFrameDrawn = { dirty -> frameTimeLogger?.onFrameDrawn(dirty) },
                )
            }
            if (uiState.loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(64.dp))

            ColoringControls(
                uiState = uiState,
                onEvent = onEvent,
                onHome = {
                    feedback(Sound.Tap)
                    onHome()
                },
                parentButtons = {
                    ParentButton(R.drawable.ic_download, R.string.export) {
                        feedback(Sound.Tap)
                        gate = GateTarget.Export
                    }
                    ParentButton(R.drawable.ic_settings, R.string.settings) {
                        feedback(Sound.Tap)
                        gate = GateTarget.Settings
                    }
                },
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(EdgePadding),
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        }
    }
}

/**
 * The floating controls: home (plus the small grown-up buttons) top-start, undo/redo top-end, the
 * palette bottom-start, the tools bottom-end with the Fit button above them while zoomed in.
 */
@Composable
internal fun ColoringControls(
    uiState: ColoringUiState,
    onEvent: (ColoringEvent) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    parentButtons: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        // A ring needs a tall screen (tablets); phones get the wheel as a 4 × 3 grid.
        val ring = maxHeight - ControlButtonSize - ActionButtonSize - EdgePadding * 2 >= PaletteRingMinHeight

        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(EdgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeButton(onHome)
            parentButtons()
        }
        UndoRedoButtons(
            canUndo = uiState.canUndo,
            canRedo = uiState.canRedo,
            onUndo = { onEvent(ColoringEvent.Undo) },
            onRedo = { onEvent(ColoringEvent.Redo) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        PaletteControl(
            color = uiState.color,
            pipetteActive = uiState.tool == Tool.Pipette,
            expanded = uiState.openPanel == Panel.Palette,
            ring = ring,
            onToggle = { onEvent(ColoringEvent.TogglePanel(Panel.Palette)) },
            onSelectColor = { onEvent(ColoringEvent.SelectColor(it)) },
            onSelectPipette = { onEvent(ColoringEvent.SelectTool(Tool.Pipette)) },
            modifier = Modifier.align(Alignment.BottomStart),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(EdgePadding),
        ) {
            FitButton(
                visible = uiState.zoomedIn && uiState.openPanel == null,
                onClick = { onEvent(ColoringEvent.FitToScreen) },
                // Centred over the tools button.
                modifier = Modifier.padding(end = (ControlButtonSize - 64.dp) / 2),
            )
            ToolsControl(
                tool = uiState.tool,
                size = uiState.size,
                color = uiState.color,
                expanded = uiState.openPanel == Panel.Tools,
                onToggle = { onEvent(ColoringEvent.TogglePanel(Panel.Tools)) },
                onSelectTool = { onEvent(ColoringEvent.SelectTool(it)) },
                onSelectSize = { onEvent(ColoringEvent.SelectSize(it)) },
            )
        }
    }
}

/** The sound for a child's action, or `null` for events that are not taps. */
private fun soundFor(event: ColoringEvent, tool: Tool): Sound? = when (event) {
    is ColoringEvent.SelectTool, is ColoringEvent.SelectSize, ColoringEvent.Undo, ColoringEvent.Redo, ColoringEvent.FitToScreen -> Sound.Tap
    // Soft sounds for opening and closing a panel and picking a colour.
    is ColoringEvent.TogglePanel, ColoringEvent.ClosePanels, is ColoringEvent.SelectColor -> Sound.Tick
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
