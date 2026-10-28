package com.example.colourfun.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.colourfun.drawing.TestOutline
import com.example.colourfun.drawing.document.Document
import com.example.colourfun.drawing.document.DocumentSize
import com.example.colourfun.drawing.layers.OutlineRasterizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the current picture so it survives configuration changes such as rotation. */
@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val documentSize: DocumentSize,
) : ViewModel() {

    private val _document = MutableStateFlow<Document?>(null)

    /** `null` while the picture is being prepared. */
    val document: StateFlow<Document?> = _document.asStateFlow()

    init {
        // Temporary: the hand-written test outline until the generator exists.
        viewModelScope.launch {
            val outline = withContext(Dispatchers.Default) {
                OutlineRasterizer.rasterize(TestOutline.lineArt, documentSize)
            }
            _document.value = Document(documentSize, outline)
        }
    }
}
