package com.fireworm.colourfun.ui.canvas

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fireworm.colourfun.data.settings.Settings
import com.fireworm.colourfun.data.settings.SettingsRepository
import com.fireworm.colourfun.domain.AutosaveUseCase
import com.fireworm.colourfun.domain.ExportToGalleryUseCase
import com.fireworm.colourfun.domain.LoadArtworkUseCase
import com.fireworm.colourfun.domain.NewArtworkUseCase
import com.fireworm.colourfun.domain.PreparedArtwork
import com.fireworm.colourfun.drawing.DrawingConfig
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.regions.RegionMaskCache
import com.fireworm.colourfun.drawing.viewport.Viewport
import com.fireworm.colourfun.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Tool {
    data class Draw(val spec: BrushSpec) : Tool
    data object Fill : Tool

    /** Picks a colour from the picture, then returns to the tool used before it. */
    data object Pipette : Tool

    /** Rubs paint out back to the paper. */
    data class Erase(val size: BrushSize) : Tool

    companion object {
        val Default = Draw(BrushSpec(Medium.Pencil, BrushSize.Medium))
    }
}

/** What a stroke on the canvas draws with, or `null` for tap tools (fill, pipette). */
val Tool.strokeSpec: BrushSpec?
    get() = when (this) {
        is Tool.Draw -> spec
        is Tool.Erase -> BrushSpec(Medium.Eraser, size)
        Tool.Fill, Tool.Pipette -> null
    }

/** The tool as stored in settings, e.g. `draw:Pencil:Medium`; the pipette is never remembered. */
internal fun Tool.encode(): String? = when (this) {
    is Tool.Draw -> "draw:${spec.medium.name}:${spec.size.name}"
    is Tool.Erase -> "erase:${size.name}"
    Tool.Fill -> "fill"
    Tool.Pipette -> null
}

internal fun decodeTool(text: String): Tool? {
    val parts = text.split(':')
    return runCatching {
        when (parts[0]) {
            "draw" -> Tool.Draw(BrushSpec(Medium.valueOf(parts[1]), BrushSize.valueOf(parts[2])))
                .takeIf { it.spec.medium != Medium.Eraser }
            "erase" -> Tool.Erase(BrushSize.valueOf(parts[1]))
            "fill" -> Tool.Fill
            else -> null
        }
    }.getOrNull()
}

/** The floating controls that open a panel; only one is open at a time. */
enum class Panel { Palette, Tools }

data class ColoringUiState(
    val loading: Boolean = true,
    val tool: Tool = Tool.Default,
    val color: Int = Palette.Default.argb,
    /** Size for the drawing mediums; kept when switching between them. */
    val size: BrushSize = BrushSize.Medium,
    val stayInsideLines: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Shows the Fit button. */
    val zoomedIn: Boolean = false,
    val openPanel: Panel? = null,
)

sealed interface ColoringEvent {
    /** Opens [panel], closing any other; closes it if it is already open. */
    data class TogglePanel(val panel: Panel) : ColoringEvent

    /** The canvas or the back button was used while a panel was open. */
    data object ClosePanels : ColoringEvent

    /** Selecting [Tool.Pipette] while it is already active returns to the previous tool. */
    data class SelectTool(val tool: Tool) : ColoringEvent
    data class SelectColor(val color: Int) : ColoringEvent
    data class SelectSize(val size: BrushSize) : ColoringEvent
    data object ToggleStayInsideLines : ColoringEvent

    /** A tap with a non-drawing tool (fill, pipette), in document pixels. */
    data class TapAt(val x: Float, val y: Float) : ColoringEvent
    data object Undo : ColoringEvent
    data object Redo : ColoringEvent

    /** Animates back to the whole picture. */
    data object FitToScreen : ColoringEvent

    /** The screen is going away (onStop): save now rather than after the autosave delay. */
    data object Save : ColoringEvent

    /** Save the picture to the device's photos. Only sent after the parent gate (and permission on 26–28). */
    data object ExportToGallery : ColoringEvent
}

/**
 * The open panel after [event]. Choosing anything in a panel, or using another control, collapses
 * it; only saving and taps that reach the canvas leave it as it is.
 */
internal fun Panel?.after(event: ColoringEvent): Panel? = when (event) {
    is ColoringEvent.TogglePanel -> if (this == event.panel) null else event.panel
    is ColoringEvent.TapAt, ColoringEvent.Save, ColoringEvent.ExportToGallery, ColoringEvent.ToggleStayInsideLines -> this
    else -> null
}

/** One-off results for the screen to show. */
sealed interface ColoringEffect {
    data object Exported : ColoringEffect
    data object ExportFailed : ColoringEffect
}

/**
 * Owns the drawing engine for the current picture, so it survives configuration changes.
 * Raw stroke input bypasses this ViewModel and goes to the engine directly.
 *
 * The artwork is saved 2 s after the last change and on [ColoringEvent.Save]. A new artwork gets its
 * id on the first save, so untouched pictures never reach "my pictures". Only that id is kept in
 * [savedState]; after process death the artwork is restored from its files.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val config: DrawingConfig,
    private val newArtwork: NewArtworkUseCase,
    private val loadArtwork: LoadArtworkUseCase,
    private val autosave: AutosaveUseCase,
    private val exportToGallery: ExportToGalleryUseCase,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColoringUiState())
    val uiState: StateFlow<ColoringUiState> = _uiState.asStateFlow()

    private val _engine = MutableStateFlow<DrawingEngine?>(null)

    /** `null` while the picture is being prepared. */
    val engine: StateFlow<DrawingEngine?> = _engine.asStateFlow()

    private val _viewport = MutableStateFlow<Viewport?>(null)

    /**
     * How the document is shown; `null` until both the document and the view size are known. View
     * state only: every artwork opens at fit, and zoom is never undone or saved.
     */
    val viewport: StateFlow<Viewport?> = _viewport.asStateFlow()

    /** The canvas size in pixels, once laid out. */
    private var viewSize: Pair<Float, Float>? = null

    private var fitJob: Job? = null

    private val _effects = Channel<ColoringEffect>(Channel.BUFFERED)
    val effects: Flow<ColoringEffect> = _effects.receiveAsFlow()

    /** The artwork being coloured. */
    private var artwork: PreparedArtwork? = null

    /** `null` until the current picture is first saved. Also the navigation argument. */
    private var artworkId: String?
        get() = savedState[ARTWORK_ID]
        set(value) {
            savedState[ARTWORK_ID] = value
        }

    /** [DrawingEngine.edits] at the last save. */
    private var savedEdits = 0L

    /** Where [Tool.Pipette] returns to. */
    private var toolBeforePipette: Tool = Tool.Default

    init {
        viewModelScope.launch {
            restore(settings.settings.first())
            rememberToolAndColor()
            val restored = artworkId?.let { loadArtwork(it) }
            if (restored == null) artworkId = null
            // Until the picture library exists, every new artwork uses the bundled test picture.
            show(restored ?: newArtwork(TEST_PICTURE_ID))
        }
        viewModelScope.launch {
            settings.settings.map { it.stayInsideLines }.distinctUntilChanged().collect { stayInside ->
                _uiState.update { it.copy(stayInsideLines = stayInside) }
            }
        }
        viewModelScope.launch {
            _engine.filterNotNull()
                .flatMapLatest { engine -> combine(engine.canUndo, engine.canRedo, ::Pair) }
                .collect { (canUndo, canRedo) -> _uiState.update { it.copy(canUndo = canUndo, canRedo = canRedo) } }
        }
        viewModelScope.launch {
            _viewport.map { it?.isZoomedIn == true }.distinctUntilChanged().collect { zoomedIn ->
                _uiState.update { it.copy(zoomedIn = zoomedIn) }
            }
        }
        viewModelScope.launch {
            _engine.filterNotNull()
                .flatMapLatest { it.edits }
                .debounce(AUTOSAVE_DELAY_MS)
                .collect { save() }
        }
    }

    fun onEvent(event: ColoringEvent) {
        _uiState.update { it.copy(openPanel = it.openPanel.after(event)) }
        when (event) {
            is ColoringEvent.TogglePanel, ColoringEvent.ClosePanels -> Unit
            is ColoringEvent.SelectTool -> selectTool(event.tool)
            is ColoringEvent.SelectColor -> _uiState.update {
                // Choosing a palette colour also ends picking one from the picture.
                it.copy(color = event.color, tool = if (it.tool == Tool.Pipette) toolBeforePipette else it.tool)
            }
            is ColoringEvent.SelectSize -> selectSize(event.size)
            ColoringEvent.ToggleStayInsideLines -> viewModelScope.launch {
                settings.setStayInsideLines(!_uiState.value.stayInsideLines)
            }
            is ColoringEvent.TapAt -> tapAt(event.x, event.y)
            ColoringEvent.Undo -> _engine.value?.undo()
            ColoringEvent.Redo -> _engine.value?.redo()
            ColoringEvent.FitToScreen -> fitToScreen()
            ColoringEvent.Save -> save()
            ColoringEvent.ExportToGallery -> export()
        }
    }

    private fun show(prepared: PreparedArtwork) {
        val document = Document(prepared.size, prepared.outline)
        prepared.paint?.let(document.paint::load)
        artwork = prepared
        savedEdits = 0
        val history = History(budgetBytes = config.historyBudgetBytes, scope = viewModelScope)
        _engine.value = DrawingEngine(document, RegionMaskCache(prepared.regions), history)
        resetViewport()
        _uiState.update { it.copy(loading = false) }
    }

    /** The canvas was laid out at a new size: show the whole document. */
    fun onViewSize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f || viewSize == width to height) return
        viewSize = width to height
        resetViewport()
    }

    /**
     * One frame of a two-finger gesture, straight from the canvas (not through [onEvent], like all
     * raw touch): zoom by [factor] around ([focusX], [focusY]), then pan, in screen pixels.
     */
    fun transformViewport(factor: Float, focusX: Float, focusY: Float, panX: Float, panY: Float) {
        fitJob?.cancel()
        _viewport.update { it?.transform(factor, focusX, focusY, panX, panY) }
    }

    private fun resetViewport() {
        fitJob?.cancel()
        val document = _engine.value?.document ?: return
        val (width, height) = viewSize ?: return
        _viewport.value = Viewport.of(document.size.width, document.size.height, width, height)
    }

    /** Springs back to fit; on the UI dispatcher, whose frame clock drives the animation. */
    private fun fitToScreen() {
        val start = _viewport.value ?: return
        val target = start.fitted()
        fitJob?.cancel()
        fitJob = viewModelScope.launch(AndroidUiDispatcher.Main) {
            animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { t, _ ->
                _viewport.value = start.lerp(target, t)
            }
            _viewport.value = target
        }
    }

    /** Saves the current artwork if it changed since the last save. */
    private fun save() {
        val engine = _engine.value ?: return
        val current = artwork ?: return
        val edits = engine.edits.value
        if (edits == savedEdits) return
        val id = artworkId ?: UUID.randomUUID().toString().also { artworkId = it }
        autosave(id, current, engine.document)
        savedEdits = edits
    }

    private fun export() {
        val engine = _engine.value ?: return
        // Saving first gives the picture an id to record the export against.
        save()
        val id = artworkId
        viewModelScope.launch {
            val effect = runCatching { exportToGallery(engine.document, id) }
                .fold(onSuccess = { ColoringEffect.Exported }, onFailure = { ColoringEffect.ExportFailed })
            _effects.send(effect)
        }
    }

    private fun restore(saved: Settings) {
        val tool = saved.lastTool?.let(::decodeTool)
        _uiState.update { state ->
            state.copy(
                tool = tool ?: state.tool,
                size = when (tool) {
                    is Tool.Draw -> tool.spec.size
                    is Tool.Erase -> tool.size
                    else -> state.size
                },
                color = saved.lastColor ?: state.color,
                stayInsideLines = saved.stayInsideLines,
            )
        }
    }

    /** Stores the tool and colour once the child settles on them (the palette emits while dragging). */
    private fun rememberToolAndColor() {
        viewModelScope.launch {
            _uiState.mapNotNull { it.tool.encode() }.distinctUntilChanged().drop(1).debounce(SETTINGS_DELAY_MS)
                .collect { settings.setLastTool(it) }
        }
        viewModelScope.launch {
            _uiState.map { it.color }.distinctUntilChanged().drop(1).debounce(SETTINGS_DELAY_MS)
                .collect { settings.setLastColor(it) }
        }
    }

    private fun selectTool(tool: Tool) {
        val current = _uiState.value.tool
        val next = when {
            tool == Tool.Pipette && current == Tool.Pipette -> toolBeforePipette
            tool == Tool.Pipette -> tool.also { toolBeforePipette = current }
            else -> tool
        }
        _uiState.update { it.copy(tool = next) }
    }

    private fun selectSize(size: BrushSize) {
        fun Tool.resized() = when (this) {
            is Tool.Draw -> Tool.Draw(spec.copy(size = size))
            is Tool.Erase -> Tool.Erase(size)
            Tool.Fill, Tool.Pipette -> this
        }
        toolBeforePipette = toolBeforePipette.resized()
        _uiState.update { it.copy(size = size, tool = it.tool.resized()) }
    }

    private fun tapAt(x: Float, y: Float) {
        val engine = _engine.value ?: return
        val state = _uiState.value
        when (state.tool) {
            Tool.Fill -> viewModelScope.launch { engine.fill(x, y, state.color) }
            Tool.Pipette -> _uiState.update { it.copy(color = engine.sampleColor(x, y), tool = toolBeforePipette) }
            is Tool.Draw, is Tool.Erase -> Unit
        }
    }

    companion object {
        /** Navigation argument and saved-state key. */
        const val ARTWORK_ID = "artworkId"

        /** The bundled test picture, until the picture library replaces it. */
        private const val TEST_PICTURE_ID = "test/cat"

        private const val AUTOSAVE_DELAY_MS = 2_000L
        private const val SETTINGS_DELAY_MS = 500L
    }
}
