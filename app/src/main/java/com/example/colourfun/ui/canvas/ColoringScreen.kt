package com.example.colourfun.ui.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Letterbox = Color(0xFFE8E8E8)

@Composable
fun ColoringScreen(viewModel: CanvasViewModel = hiltViewModel()) {
    val document by viewModel.document.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = Letterbox) {
        document?.let { DrawingSurface(it, Modifier.fillMaxSize().safeDrawingPadding()) }
    }
}
