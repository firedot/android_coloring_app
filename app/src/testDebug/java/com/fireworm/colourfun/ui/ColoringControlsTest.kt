package com.fireworm.colourfun.ui

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.ui.canvas.ColoringControls
import com.fireworm.colourfun.ui.canvas.ColoringEvent
import com.fireworm.colourfun.ui.canvas.ColoringUiState
import com.fireworm.colourfun.ui.canvas.Panel
import com.fireworm.colourfun.ui.canvas.Tool
import com.fireworm.colourfun.ui.canvas.after
import com.fireworm.colourfun.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

/**
 * The floating controls with the ViewModel's panel rule ([after]) and a minimal stand-in for its
 * tool, colour and size handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class ColoringControlsTest {

    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(ColoringUiState(loading = false))
    private var homeClicks = 0
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun onEvent(event: ColoringEvent) {
        val s = state.copy(openPanel = state.openPanel.after(event))
        state = when (event) {
            is ColoringEvent.SelectTool -> s.copy(tool = event.tool)
            is ColoringEvent.SelectColor -> s.copy(color = event.color)
            is ColoringEvent.SelectSize -> s.copy(
                size = event.size,
                tool = (s.tool as? Tool.Draw)?.let { Tool.Draw(it.spec.copy(size = event.size)) } ?: s.tool,
            )
            else -> s
        }
    }

    /** Landscape phone by default; a tablet-sized screen shows the wheel as a ring. */
    private fun show(width: Dp = 800.dp, height: Dp = 380.dp) = compose.setContent {
        ColoringControls(uiState = state, onEvent = ::onEvent, onHome = { homeClicks++ }, modifier = Modifier.size(width, height))
    }

    @Test
    fun `the palette opens, and choosing a colour selects it and closes the panel`() {
        show()
        compose.onNodeWithContentDescription("Red").assertDoesNotExist()

        compose.onNodeWithContentDescription("Colours").performClick()
        Palette.Wheel.forEach { compose.onNodeWithContentDescription(context.getString(it.name)).assertExists() }
        compose.onNodeWithContentDescription("Blue").assertIsSelected()

        compose.onNodeWithContentDescription("Yellow-green").performClick()

        assertEquals(Palette.YellowGreen.argb, state.color)
        assertNull(state.openPanel)
        compose.onNodeWithContentDescription("Yellow-green").assertDoesNotExist()
    }

    @Test
    fun `the neutrals and the pipette come after the wheel`() {
        show()
        compose.onNodeWithContentDescription("Colours").performClick()

        listOf("Black", "White", "Grey", "Brown", "Pink").forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithContentDescription("Pick a colour from the picture").performClick()

        assertEquals(Tool.Pipette, state.tool)
        assertNull(state.openPanel)
    }

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `the wheel is a ring on a tall screen and still has every colour`() {
        show(width = 1280.dp, height = 800.dp)
        compose.onNodeWithContentDescription("Colours").performClick()

        Palette.Wheel.forEach { compose.onNodeWithContentDescription(context.getString(it.name)).assertExists() }
    }

    @Test
    fun `tapping the open panel's button closes it`() {
        show()

        compose.onNodeWithContentDescription("Tools").performClick()
        assertEquals(Panel.Tools, state.openPanel)
        compose.onNodeWithContentDescription("Tools").performClick()

        assertNull(state.openPanel)
        compose.onNodeWithContentDescription("Chalk").assertDoesNotExist()
    }

    @Test
    fun `only one panel is open at a time`() {
        show()

        compose.onNodeWithContentDescription("Colours").performClick()
        compose.onNodeWithContentDescription("Tools").performClick()

        assertEquals(Panel.Tools, state.openPanel)
        compose.onNodeWithContentDescription("Red").assertDoesNotExist()
        compose.onNodeWithContentDescription("Chalk").assertExists()
    }

    @Test
    fun `the tools panel lists every tool and the current one is on`() {
        show()
        compose.onNodeWithContentDescription("Tools").performClick()

        listOf("Pencil", "Pen", "Chalk", "Spray", "Brush", "Paint brush", "Magic pen", "Eraser", "Fill")
            .forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithContentDescription("Pencil").assertIsOn()
    }

    @Test
    fun `choosing a tool selects it and closes the panel`() {
        show()
        compose.onNodeWithContentDescription("Tools").performClick()

        compose.onNodeWithContentDescription("Chalk").performClick()

        assertEquals(Tool.Draw(BrushSpec(Medium.Chalk, BrushSize.Medium)), state.tool)
        assertNull(state.openPanel)
    }

    @Test
    fun `the chosen size carries over to the next medium`() {
        show()
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithContentDescription("Thick").performClick()

        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithContentDescription("Spray").performClick()

        assertEquals(Tool.Draw(BrushSpec(Medium.Spray, BrushSize.Large)), state.tool)
    }

    @Test
    fun `fill disables the size dots`() {
        show()
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithContentDescription("Fill").performClick()

        compose.onNodeWithContentDescription("Tools").performClick()

        compose.onNodeWithContentDescription("Thin").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Fill").assertIsOn()
    }

    @Test
    fun `undo and redo follow the history`() {
        show()
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()

        state = state.copy(canUndo = true)

        compose.onNodeWithContentDescription("Undo").assertIsEnabled()
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test
    fun `undo also closes an open panel`() {
        state = state.copy(canUndo = true)
        show()
        compose.onNodeWithContentDescription("Colours").performClick()

        compose.onNodeWithContentDescription("Undo").performClick()

        assertNull(state.openPanel)
    }

    @Test
    fun `the Fit button shows only while zoomed in and no panel is open`() {
        show()
        compose.onNodeWithContentDescription("Show the whole picture").assertDoesNotExist()

        state = state.copy(zoomedIn = true)
        compose.onNodeWithContentDescription("Show the whole picture").assertExists()

        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithContentDescription("Show the whole picture").assertDoesNotExist()
    }

    @Test
    fun `home is always there`() {
        show()

        compose.onNodeWithContentDescription("Home").performClick()

        assertEquals(1, homeClicks)
    }

    @Test
    fun `while the pipette is active the current colour stays marked`() {
        state = state.copy(tool = Tool.Pipette, color = Palette.Red.argb)
        show()
        compose.onNodeWithContentDescription("Colours").performClick()

        compose.onNodeWithContentDescription("Red").assertIsSelected()
        compose.onNodeWithContentDescription("Black").assertIsNotSelected()
        compose.onNodeWithContentDescription("Pick a colour from the picture").assertIsOn()
    }
}
