package com.fireworm.colourfun.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.fireworm.colourfun.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole app with the bundled library: picture → canvas → home → "my pictures" → continue.
 * Real Hilt graph, Room and files (Robolectric gives each test a fresh app directory).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_LANDSCAPE)
class LibraryToCanvasTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `an untouched picture is not saved`() {
        openFirstPicture()

        compose.onNodeWithContentDescription("Home").performClick()

        waitFor(OPEN)
        compose.onNodeWithContentDescription(CONTINUE).assertDoesNotExist()
    }

    @Test
    fun `a painted picture appears in my pictures and opens again`() {
        openFirstPicture()
        paintStroke()

        compose.onNodeWithContentDescription("Home").performClick()
        waitFor(CONTINUE)
        compose.onAllNodesWithContentDescription(CONTINUE).assertCountEquals(1)

        compose.onNodeWithContentDescription(CONTINUE).performClick()
        waitFor(CANVAS)
    }

    @Test
    fun `a picture made on a wide phone is letterboxed on a 4 by 3 tablet`() {
        openFirstPicture()
        paintStroke()
        compose.onNodeWithContentDescription("Home").performClick()
        waitFor(CONTINUE)

        // The same artwork, now on a tablet: the saved document keeps its 20:9 shape.
        RuntimeEnvironment.setQualifiers(TABLET_LANDSCAPE)
        compose.onNodeWithContentDescription(CONTINUE).performClick()
        waitFor(CANVAS)
        compose.waitForIdle()

        val screen = compose.onRoot().captureToImage().asAndroidBitmap()
        val centreX = screen.width / 2
        assertEquals("letterbox above the document", LETTERBOX, screen.getPixel(centreX, 4))
        assertEquals("letterbox below the document", LETTERBOX, screen.getPixel(centreX, screen.height - 4))
        assertNotEquals("the document in the middle", LETTERBOX, screen.getPixel(centreX, screen.height / 2))
    }

    private fun openFirstPicture() {
        waitFor(OPEN)
        compose.onAllNodesWithContentDescription(OPEN).onFirst().performClick()
        waitFor(CANVAS)
    }

    /** A stroke across the middle of the canvas, with the default pencil. */
    private fun paintStroke() {
        compose.onNodeWithContentDescription(CANVAS).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.5f), Offset(width * 0.7f, height * 0.55f), durationMillis = 400)
        }
        // The stroke is in the history once undo is enabled.
        compose.waitUntil(TIMEOUT_MS) { runCatching { compose.onNodeWithContentDescription("Undo").assertIsEnabled() }.isSuccess }
    }

    /** Pictures are decoded and labelled in the background, saves land asynchronously. */
    private fun waitFor(description: String) {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val OPEN = "Colour this picture"
        const val CONTINUE = "Keep colouring"
        const val CANVAS = "Picture to colour"
        const val TIMEOUT_MS = 20_000L
        const val LETTERBOX = 0xFFE8E8E8.toInt()
    }
}
