package com.fireworm.colourfun.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.regions.RegionMap
import com.fireworm.colourfun.drawing.regions.RegionMaskCache
import com.fireworm.colourfun.drawing.viewport.Viewport
import com.fireworm.colourfun.ui.canvas.DrawingSurface
import com.fireworm.colourfun.ui.canvas.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** One finger draws, two fingers zoom and pan; a second finger decides what happens to the stroke. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GestureArbitrationTest {

    @get:Rule val compose = createComposeRule()

    private val size = DocumentSize(400, 300, scale = 0.2f)
    private val engine: DrawingEngine = run {
        val outline = createBitmap(size.width, size.height, Bitmap.Config.ALPHA_8)
        val dispatcher = StandardTestDispatcher()
        DrawingEngine(
            Document(size, outline),
            RegionMaskCache(RegionMap.build(outline, outlineWidthPx = 4f), workDispatcher = dispatcher),
            History(scope = TestScope(dispatcher), compressDispatcher = dispatcher),
        )
    }
    private val viewport = MutableStateFlow<Viewport?>(null)
    private var tapped: Offset? = null

    private fun show(tool: Tool = Tool.Pen) = compose.setContent {
        DrawingSurface(
            engine = engine,
            viewport = viewport,
            tool = tool,
            color = 0xFFE53935.toInt(),
            stayInsideLines = false,
            onTap = { x, y -> tapped = Offset(x, y) },
            onViewSize = { w, h -> viewport.value = Viewport.of(size.width, size.height, w, h) },
            onTransform = { factor, focus, pan -> viewport.update { it?.transform(factor, focus.x, focus.y, pan.x, pan.y) } },
            modifier = Modifier.size(400.dp, 300.dp).testTag(CANVAS),
        )
    }

    @Test
    fun `one finger draws a stroke`() {
        show()

        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(0, center)
            repeat(10) { moveBy(0, Offset(10f, 4f)) }
            up(0)
        }

        assertTrue(engine.canUndo.value)
        assertTrue(hasPaint())
    }

    @Test
    fun `a short stroke is discarded when a second finger lands`() {
        show()

        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(0, center)
            moveBy(0, Offset(3f, 0f), delayMillis = 30)
            down(1, center + Offset(150f, 0f))
            up(0)
            up(1)
        }

        assertFalse(engine.canUndo.value)
        assertFalse(hasPaint())
    }

    @Test
    fun `a long stroke is kept when a second finger lands`() {
        show()

        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(0, center)
            // 12 moves of 20 ms: 240 ms and far more than 24 dp.
            repeat(12) { moveBy(0, Offset(15f, 0f), delayMillis = 20) }
            down(1, center + Offset(-150f, 60f))
            up(0)
            up(1)
        }

        assertTrue(engine.canUndo.value)
        assertTrue(hasPaint())
    }

    @Test
    fun `two fingers zoom and paint nothing`() {
        show()

        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(0, center - Offset(50f, 0f))
            down(1, center + Offset(50f, 0f))
            repeat(10) {
                moveBy(0, Offset(-15f, 0f), delayMillis = 0)
                moveBy(1, Offset(15f, 0f))
            }
            up(0)
            up(1)
        }

        assertTrue("scale ${viewport.value?.scale}", checkNotNull(viewport.value).scale > 1.5f)
        assertFalse(engine.canUndo.value)
        assertFalse(hasPaint())
    }

    @Test
    fun `after a pinch the remaining finger does not draw`() {
        show()

        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(0, center - Offset(50f, 0f))
            down(1, center + Offset(50f, 0f))
            up(1)
            repeat(10) { moveBy(0, Offset(10f, 10f)) }
            up(0)
        }

        assertFalse(engine.canUndo.value)
        assertFalse(hasPaint())
    }

    @Test
    fun `fill acts when the finger lifts, at the touch-down point`() {
        show(Tool.Fill)

        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(0, center)
            moveBy(0, Offset(5f, 5f))
            up(0)
        }

        val view = checkNotNull(viewport.value)
        val expected = checkNotNull(tapped)
        assertEquals(size.width / 2f, expected.x, 1f / view.zoom + 0.5f)
        assertEquals(size.height / 2f, expected.y, 1f / view.zoom + 0.5f)
    }

    @Test
    fun `the first finger of a pinch never fills`() {
        show(Tool.Fill)

        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(0, center - Offset(50f, 0f))
            down(1, center + Offset(50f, 0f))
            moveBy(1, Offset(30f, 0f))
            up(0)
            up(1)
        }

        assertNull(tapped)
    }

    private fun hasPaint(): Boolean {
        val bitmap = engine.document.paint.bitmap
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { it != 0 }
    }

    private companion object {
        const val CANVAS = "canvas"
    }
}
