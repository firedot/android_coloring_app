package com.example.colourfun

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.colourfun.drawing.TestOutline
import com.example.colourfun.drawing.document.Document
import com.example.colourfun.drawing.document.DocumentSize
import com.example.colourfun.drawing.layers.OutlineRasterizer
import com.example.colourfun.ui.canvas.DrawingSurface
import com.example.colourfun.ui.theme.ColourFunTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Letterbox = Color(0xFFE8E8E8)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isLowRamDevice = getSystemService(ActivityManager::class.java).isLowRamDevice
        setContent {
            ColourFunTheme {
                // Temporary: shows the hand-written test outline until the Coloring screen,
                // CanvasViewModel and the generator exist.
                Surface(modifier = Modifier.fillMaxSize(), color = Letterbox) {
                    val document by produceState<Document?>(initialValue = null) {
                        val size = DocumentSize.forDevice(isLowRamDevice)
                        val outline = withContext(Dispatchers.Default) {
                            OutlineRasterizer.rasterize(TestOutline.lineArt, size)
                        }
                        value = Document(size, outline)
                    }
                    document?.let { DrawingSurface(it, Modifier.fillMaxSize().safeDrawingPadding()) }
                }
            }
        }
    }
}
