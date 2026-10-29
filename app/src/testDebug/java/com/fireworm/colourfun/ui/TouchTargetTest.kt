package com.fireworm.colourfun.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.ui.canvas.Tool
import com.fireworm.colourfun.ui.palette.SpectrumPalette
import com.fireworm.colourfun.ui.tools.SizePicker
import com.fireworm.colourfun.ui.tools.ToolBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Primary controls are at least 64 dp, everything else at least 48 dp. */
@RunWith(RobolectricTestRunner::class)
class TouchTargetTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `tools are primary controls`() {
        compose.setContent { ToolBar(tool = Tool.Pen, size = BrushSize.Medium, onSelectTool = {}, vertical = false) }

        listOf(
            "Pencil", "Pen", "Chalk", "Spray", "Brush", "Paint brush", "Magic pen", "Eraser", "Fill",
            "Pick a colour from the picture",
        ).forEach { compose.onNodeWithContentDescription(it).assertAtLeast(PRIMARY) }
    }

    @Test
    fun `size buttons are secondary controls`() {
        compose.setContent { SizePicker(size = BrushSize.Medium, color = 0xFF000000.toInt(), enabled = true, onSelect = {}) }

        listOf("Thin", "Medium", "Thick").forEach { compose.onNodeWithContentDescription(it).assertAtLeast(SECONDARY) }
    }

    @Test
    fun `palette strips are primary controls in both orientations`() {
        compose.setContent {
            Column {
                SpectrumPalette(selected = 0xFF2196F3.toInt(), onSelect = {}, vertical = false, modifier = Modifier.fillMaxWidth())
            }
        }

        listOf("Colour", "Light or dark").forEach {
            compose.onNodeWithContentDescription(it).assertHeightIsAtLeast(PRIMARY)
        }
    }

    @Test
    fun `vertical palette strips are wide enough`() {
        compose.setContent { SpectrumPalette(selected = 0xFF2196F3.toInt(), onSelect = {}, vertical = true) }

        listOf("Colour", "Light or dark").forEach {
            compose.onNodeWithContentDescription(it).assertWidthIsAtLeast(PRIMARY)
        }
    }

    private fun SemanticsNodeInteraction.assertAtLeast(size: Dp) {
        assertWidthIsAtLeast(size)
        assertHeightIsAtLeast(size)
    }

    private companion object {
        val PRIMARY = 64.dp
        val SECONDARY = 48.dp
    }
}
