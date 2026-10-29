package com.fireworm.colourfun.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.ui.canvas.ColoringControls
import com.fireworm.colourfun.ui.canvas.ColoringUiState
import com.fireworm.colourfun.ui.canvas.Panel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Floating buttons that open a panel are 72 dp, other primary controls (tools, home, undo, redo) at
 * least 64 dp, and every panel item at least 56 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class TouchTargetTest {

    @get:Rule val compose = createComposeRule()

    private fun show(openPanel: Panel? = null) = compose.setContent {
        ColoringControls(
            uiState = ColoringUiState(loading = false, openPanel = openPanel, zoomedIn = true),
            onEvent = {},
            onHome = {},
            modifier = Modifier.size(800.dp, 380.dp),
        )
    }

    @Test
    fun `panel buttons are 72 dp`() {
        show()

        listOf("Colours", "Tools").forEach { compose.onNodeWithContentDescription(it).assertAtLeast(72.dp) }
    }

    @Test
    fun `home, undo, redo and fit are primary controls`() {
        show()

        listOf("Home", "Undo", "Redo", "Show the whole picture").forEach {
            compose.onNodeWithContentDescription(it).assertAtLeast(PRIMARY)
        }
    }

    @Test
    fun `tools are primary controls and the size dots are panel items`() {
        show(openPanel = Panel.Tools)

        listOf("Pencil", "Pen", "Chalk", "Spray", "Brush", "Paint brush", "Magic pen", "Eraser", "Fill")
            .forEach { compose.onNodeWithContentDescription(it).assertAtLeast(PRIMARY) }
        listOf("Thin", "Medium", "Thick").forEach { compose.onNodeWithContentDescription(it).assertAtLeast(PANEL_ITEM) }
    }

    @Test
    fun `colours and the pipette are panel items`() {
        show(openPanel = Panel.Palette)

        listOf("Red", "Yellow-green", "Red-violet", "Black", "Pink", "Pick a colour from the picture")
            .forEach { compose.onNodeWithContentDescription(it).assertAtLeast(PANEL_ITEM) }
    }

    private fun SemanticsNodeInteraction.assertAtLeast(size: Dp) {
        assertWidthIsAtLeast(size)
        assertHeightIsAtLeast(size)
    }

    private companion object {
        val PRIMARY = 64.dp
        val PANEL_ITEM = 56.dp
    }
}
