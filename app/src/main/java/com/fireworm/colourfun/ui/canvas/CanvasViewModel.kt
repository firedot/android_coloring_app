package com.fireworm.colourfun.ui.canvas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fireworm.colourfun.data.settings.Settings
import com.fireworm.colourfun.data.settings.SettingsRepository
import com.fireworm.colourfun.domain.LoadPictureUseCase
import com.fireworm.colourfun.domain.NewPictureUseCase
import com.fireworm.colourfun.domain.Picture
import com.fireworm.colourfun.domain.SaveArtworkUseCase
import com.fireworm.colourfun.drawing.DrawingConfig
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.regions.RegionMaskCache
import com.fireworm.colourfun.generator.LineArt
import com.fireworm.colourfun.ui.palette.DefaultColor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
        val Pen = Draw(BrushSpec(Medium.Pen, BrushSize.Medium))
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

data class ColoringUiState(
    val loading: Boolean = true,
    val tool: Tool = Tool.Pen,
    val color: Int = DefaultColor,
    /** Size for the drawing mediums; kept when switching between them. */
    val size: BrushSize = BrushSize.Medium,
    val stayInsideLines: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed interface ColoringEvent {
    /** Selecting [Tool.Pipette] while it is already active returns to the previous tool. */
    data class SelectTool(val tool: Tool) : ColoringEvent
    data class SelectColor(val color: Int) : ColoringEvent
    data class SelectSize(val size: BrushSize) : ColoringEvent
    data object ToggleStayInsideLines : ColoringEvent

    /** A tap with a non-drawing tool (fill, pipette), in document pixels. */
    data class TapAt(val x: Float, val y: Float) : ColoringEvent
    data object Undo : ColoringEvent
    data object Redo : ColoringEvent
    data object NewAnimal : ColoringEvent

    /** The screen is going away (onStop): save now rather than after the autosave delay. */
    data object Save : ColoringEvent
}

/**
 * Owns the drawing engine for the current picture, so it survives configuration changes.
 * Raw stroke input bypasses this ViewModel and goes to the engine directly.
 *
 * The picture is saved 2 s after the last change and on [ColoringEvent.Save]. A new picture gets its
 * artwork id on the first save, so untouched animals never reach the gallery. Only that id is kept
 * in [savedState]; after process death the picture is restored from its files.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val config: DrawingConfig,
    private val newPicture: NewPictureUseCase,
    private val loadPicture: LoadPictureUseCase,
    private val saveArtwork: SaveArtworkUseCase,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColoringUiState())
    val uiState: StateFlow<ColoringUiState> = _uiState.asStateFlow()

    private val _engine = MutableStateFlow<DrawingEngine?>(null)

    /** `null` while the picture is being prepared. */
    val engine: StateFlow<DrawingEngine?> = _engine.asStateFlow()

    private var pictureJob: Job? = null

    /** The line art being coloured, saved as `outline.json`. */
    private var lineArt: LineArt? = null

    /** `null` until the current picture is first saved. Also the navigation argument. */
    private var artworkId: String?
        get() = savedState[ARTWORK_ID]
        set(value) {
            savedState[ARTWORK_ID] = value
        }

    /** [DrawingEngine.edits] at the last save. */
    private var savedEdits = 0L

    /** Where [Tool.Pipette] returns to. */
    private var toolBeforePipette: Tool = Tool.Pen

    init {
        pictureJob = viewModelScope.launch {
            restore(settings.settings.first())
            rememberToolAndColor()
            val picture = artworkId?.let { loadPicture(it) }
            if (picture == null) artworkId = null
            show(picture ?: newPicture())
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
            _engine.filterNotNull()
                .flatMapLatest { it.edits }
                .debounce(AUTOSAVE_DELAY_MS)
                .collect { save() }
        }
    }

    fun onEvent(event: ColoringEvent) {
        when (event) {
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
            ColoringEvent.NewAnimal -> newAnimal()
            ColoringEvent.Save -> save()
        }
    }

    /** Replaces the picture with a newly generated animal; the old one stays on screen until it is ready. */
    private fun newAnimal() {
        save()
        pictureJob?.cancel()
        _uiState.update { it.copy(loading = true) }
        pictureJob = viewModelScope.launch {
            val picture = newPicture()
            // Anything drawn on the old picture while the new one was generated.
            save()
            artworkId = null
            show(picture)
        }
    }

    private fun show(picture: Picture) {
        _engine.value?.cancelStroke()
        val document = Document(config.documentSize, picture.outline)
        picture.paint?.let(document.paint::load)
        lineArt = picture.lineArt
        savedEdits = 0
        val history = History(budgetBytes = config.historyBudgetBytes, scope = viewModelScope)
        _engine.value = DrawingEngine(document, RegionMaskCache(picture.regions), history)
        _uiState.update { it.copy(loading = false) }
    }

    /** Saves the current picture if it changed since the last save. */
    private fun save() {
        val engine = _engine.value ?: return
        val art = lineArt ?: return
        val edits = engine.edits.value
        if (edits == savedEdits) return
        val id = artworkId ?: UUID.randomUUID().toString().also { artworkId = it }
        saveArtwork(id, art, engine.document)
        savedEdits = edits
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

        private const val AUTOSAVE_DELAY_MS = 2_000L
        private const val SETTINGS_DELAY_MS = 500L
    }
}
