package com.fireworm.colourfun.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fireworm.colourfun.drawing.DrawingConfig
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.TestOutline
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.layers.OutlineRasterizer
import com.fireworm.colourfun.drawing.regions.RegionMap
import com.fireworm.colourfun.drawing.regions.RegionMaskCache
import com.fireworm.colourfun.ui.palette.DefaultColor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Tool {
    data class Draw(val spec: BrushSpec) : Tool
    data object Fill : Tool

    /** Picks a colour from the picture, then returns to the tool used before it. */
    data object Pipette : Tool

    companion object {
        val Pen = Draw(BrushSpec(Medium.Pen, BrushSize.Medium))
    }
}

data class ColoringUiState(
    val loading: Boolean = true,
    val tool: Tool = Tool.Pen,
    val color: Int = DefaultColor,
    val stayInsideLines: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed interface ColoringEvent {
    /** Selecting [Tool.Pipette] while it is already active returns to the previous tool. */
    data class SelectTool(val tool: Tool) : ColoringEvent
    data class SelectColor(val color: Int) : ColoringEvent
    data object ToggleStayInsideLines : ColoringEvent

    /** A tap with a non-drawing tool (fill, pipette), in document pixels. */
    data class TapAt(val x: Float, val y: Float) : ColoringEvent
    data object Undo : ColoringEvent
    data object Redo : ColoringEvent
}

/**
 * Owns the drawing engine for the current picture, so it survives configuration changes.
 * Raw stroke input bypasses this ViewModel and goes to the engine directly.
 */
@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val config: DrawingConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColoringUiState())
    val uiState: StateFlow<ColoringUiState> = _uiState.asStateFlow()

    private val _engine = MutableStateFlow<DrawingEngine?>(null)

    /** `null` while the picture is being prepared. */
    val engine: StateFlow<DrawingEngine?> = _engine.asStateFlow()

    init {
        viewModelScope.launch {
            // Temporary: the hand-written test outline until the generator exists.
            val size = config.documentSize
            val lineArt = TestOutline.lineArt
            val (outline, regions) = withContext(Dispatchers.Default) {
                val outline = OutlineRasterizer.rasterize(lineArt, size)
                outline to RegionMap.build(outline, outlineWidthPx = lineArt.strokeWidth * size.scale)
            }
            val history = History(budgetBytes = config.historyBudgetBytes, scope = viewModelScope)
            val engine = DrawingEngine(Document(size, outline), RegionMaskCache(regions), history)
            _engine.value = engine
            _uiState.update { it.copy(loading = false) }

            combine(engine.canUndo, engine.canRedo, ::Pair).collect { (canUndo, canRedo) ->
                _uiState.update { it.copy(canUndo = canUndo, canRedo = canRedo) }
            }
        }
    }

    /** Where [Tool.Pipette] returns to. */
    private var toolBeforePipette: Tool = Tool.Pen

    fun onEvent(event: ColoringEvent) {
        when (event) {
            is ColoringEvent.SelectTool -> selectTool(event.tool)
            is ColoringEvent.SelectColor -> _uiState.update {
                // Choosing a palette colour also ends picking one from the picture.
                it.copy(color = event.color, tool = if (it.tool == Tool.Pipette) toolBeforePipette else it.tool)
            }
            ColoringEvent.ToggleStayInsideLines -> _uiState.update { it.copy(stayInsideLines = !it.stayInsideLines) }
            is ColoringEvent.TapAt -> tapAt(event.x, event.y)
            ColoringEvent.Undo -> _engine.value?.undo()
            ColoringEvent.Redo -> _engine.value?.redo()
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

    private fun tapAt(x: Float, y: Float) {
        val engine = _engine.value ?: return
        val state = _uiState.value
        when (state.tool) {
            Tool.Fill -> viewModelScope.launch { engine.fill(x, y, state.color) }
            Tool.Pipette -> _uiState.update { it.copy(color = engine.sampleColor(x, y), tool = toolBeforePipette) }
            is Tool.Draw -> Unit
        }
    }
}
