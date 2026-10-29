package com.fireworm.colourfun.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.ui.canvas.Tool
import com.fireworm.colourfun.ui.canvas.strokeSpec
import com.fireworm.colourfun.ui.tools.SizePicker
import com.fireworm.colourfun.ui.tools.ToolBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Compose UI test on Robolectric; debug only, where the test activity is in the manifest. */
@RunWith(RobolectricTestRunner::class)
class ToolSelectionTest {

    @get:Rule val compose = createComposeRule()

    private var tool: Tool = Tool.Pen
    private var size = BrushSize.Medium

    private fun showTools() = compose.setContent {
        var current by remember { mutableStateOf(tool) }
        var currentSize by remember { mutableStateOf(size) }
        Column {
            ToolBar(tool = current, size = currentSize, onSelectTool = { current = it; tool = it }, vertical = false)
            SizePicker(
                size = currentSize,
                color = 0xFF2196F3.toInt(),
                enabled = current.strokeSpec != null,
                onSelect = { currentSize = it; size = it },
            )
        }
    }

    @Test
    fun `tapping a medium selects it and only it`() {
        showTools()
        compose.onNodeWithContentDescription("Pen").assertIsOn()

        compose.onNodeWithContentDescription("Pencil").performClick()

        compose.onNodeWithContentDescription("Pencil").assertIsOn()
        compose.onNodeWithContentDescription("Pen").assertIsOff()
        assertEquals(Tool.Draw(BrushSpec(Medium.Pencil, BrushSize.Medium)), tool)
    }

    @Test
    fun `the chosen size carries over to the next medium`() {
        showTools()

        compose.onNodeWithContentDescription("Thick").performClick()
        compose.onNodeWithContentDescription("Chalk").performClick()

        assertEquals(Tool.Draw(BrushSpec(Medium.Chalk, BrushSize.Large)), tool)
        compose.onNodeWithContentDescription("Thick").assertIsOn()
    }

    @Test
    fun `fill disables the size picker`() {
        showTools()

        compose.onNodeWithContentDescription("Fill").performClick()

        assertEquals(Tool.Fill, tool)
        compose.onNodeWithContentDescription("Fill").assertIsOn()
        compose.onNodeWithContentDescription("Thin").assertIsNotEnabled()
    }
}
