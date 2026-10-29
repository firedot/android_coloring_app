package com.example.colourfun.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.colourfun.drawing.DrawingConfig
import com.example.colourfun.drawing.DrawingEngine
import com.example.colourfun.drawing.TestOutline
import com.example.colourfun.drawing.brushes.BrushSize
import com.example.colourfun.drawing.brushes.BrushSpec
import com.example.colourfun.drawing.brushes.Medium
import com.example.colourfun.drawing.document.Document
import com.example.colourfun.drawing.history.History
import com.example.colourfun.drawing.layers.OutlineRasterizer
import com.example.colourfun.drawing.regions.RegionMap
import com.example.colourfun.drawing.regions.RegionMaskCache
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

    companion object {
        val Pen = Draw(BrushSpec(Medium.Pen, BrushSize.Medium))
    }
}

data class ColoringUiState(
    val loading: Boolean = true,
    val tool: Tool = Tool.Pen,
    val color: Int = DEFAULT_COLOR,
    val stayInsideLines: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed interface ColoringEvent {
    data class SelectTool(val tool: Tool) : ColoringEvent
    data object ToggleStayInsideLines : ColoringEvent

    /** A fill tap, in document pixels. */
    data class FillAt(val x: Float, val y: Float) : ColoringEvent
    data object Undo : ColoringEvent
    data object Redo : ColoringEvent
}

/** Fixed until the palette exists. */
private const val DEFAULT_COLOR = 0xFF1E88E5.toInt()

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

    fun onEvent(event: ColoringEvent) {
        when (event) {
            is ColoringEvent.SelectTool -> _uiState.update { it.copy(tool = event.tool) }
            ColoringEvent.ToggleStayInsideLines -> _uiState.update { it.copy(stayInsideLines = !it.stayInsideLines) }
            is ColoringEvent.FillAt -> {
                val engine = _engine.value ?: return
                val color = _uiState.value.color
                viewModelScope.launch { engine.fill(event.x, event.y, color) }
            }
            ColoringEvent.Undo -> _engine.value?.undo()
            ColoringEvent.Redo -> _engine.value?.redo()
        }
    }
}
