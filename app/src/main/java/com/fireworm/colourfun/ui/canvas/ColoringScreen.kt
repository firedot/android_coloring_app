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
import com.fireworm.colourfun.ui.controls.FitButton
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
    val gallery = @Composable { modifier: Modifier ->
        GalleryButton(modifier) {
            feedback(Sound.Tap)
            onOpenGallery()
        }
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
                    // Measures frame time (texture upload) while drawing and while zooming.
                    onActiveChange = { active -> frameTimeLogger?.setActive(active) },
                    onFrameDrawn = { dirty -> frameTimeLogger?.onFrameDrawn(dirty) },
                )
            }
            if (uiState.loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(64.dp))

            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (maxWidth >= maxHeight) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxHeight().padding(EdgePadding),
                            verticalArrangement = Arrangement.spacedBy(EdgePadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            gallery(Modifier.size(width = 120.dp, height = HistoryButtonSize))
                            palette(true, Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(EdgePadding)) {
                                HistoryButtons(uiState, onEvent)
                            }
                        }
                        Spacer(Modifier.weight(1f))
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
                    // Only reachable where the system ignores the landscape lock (large screens, Android 16+).
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
                                gallery(Modifier.size(width = 72.dp, height = HistoryButtonSize))
                                Spacer(Modifier.weight(1f))
                                parentButtons()
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        palette(false, Modifier.fillMaxWidth().padding(EdgePadding))
                    }
                }
            }
            FitButton(
                visible = uiState.zoomedIn,
                onClick = { onEvent(ColoringEvent.FitToScreen) },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(EdgePadding),
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        }
    }
}

/** The sound for a child's action, or `null` for events that are not taps. */
private fun soundFor(event: ColoringEvent, tool: Tool): Sound? = when (event) {
    is ColoringEvent.SelectTool, is ColoringEvent.SelectSize, ColoringEvent.Undo, ColoringEvent.Redo, ColoringEvent.FitToScreen -> Sound.Tap
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
