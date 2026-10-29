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

data class ColoringUiState(
    val loading: Boolean = true,
    val brush: BrushSpec = BrushSpec(Medium.Pen, BrushSize.Medium),
    val color: Int = DEFAULT_COLOR,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed interface ColoringEvent {
    data object Undo : ColoringEvent
    data object Redo : ColoringEvent
}

/** Fixed until the palette exists. */
private const val DEFAULT_COLOR = 0xFF1E88E5.toInt()

/**
 * Owns the drawing engine for the current picture, so it survives configuration changes.
 * Raw touch input bypasses this ViewModel and goes to the engine directly.
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
            val outline = withContext(Dispatchers.Default) {
                OutlineRasterizer.rasterize(TestOutline.lineArt, size)
            }
            val history = History(budgetBytes = config.historyBudgetBytes, scope = viewModelScope)
            val engine = DrawingEngine(Document(size, outline), history)
            _engine.value = engine
            _uiState.update { it.copy(loading = false) }

            combine(engine.canUndo, engine.canRedo, ::Pair).collect { (canUndo, canRedo) ->
                _uiState.update { it.copy(canUndo = canUndo, canRedo = canRedo) }
            }
        }
    }

    fun onEvent(event: ColoringEvent) {
        val engine = _engine.value ?: return
        when (event) {
            ColoringEvent.Undo -> engine.undo()
            ColoringEvent.Redo -> engine.redo()
        }
    }
}
