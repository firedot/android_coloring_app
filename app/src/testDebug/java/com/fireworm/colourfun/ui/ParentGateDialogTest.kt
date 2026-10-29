package com.fireworm.colourfun.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.fireworm.colourfun.ui.parentgate.ParentGateChallenge
import com.fireworm.colourfun.ui.parentgate.ParentGateDialog
import com.fireworm.colourfun.ui.parentgate.TAG_INPUT
import com.fireworm.colourfun.ui.parentgate.TAG_WORDS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Compose UI test on Robolectric; debug only, where the test activity is in the manifest. */
@RunWith(RobolectricTestRunner::class)
class ParentGateDialogTest {

    @get:Rule val compose = createComposeRule()

    private val challenges = ArrayDeque(listOf(listOf(7, 2, 9, 4), listOf(1, 0, 0, 8)))
    private var passed = 0
    private var dismissed = 0

    private fun showGate() = compose.setContent {
        ParentGateDialog(
            onPass = { passed++ },
            onDismiss = { dismissed++ },
            challengeFactory = { ParentGateChallenge(challenges.removeFirst()) },
        )
    }

    @Test
    fun `the right digits open the gate`() {
        showGate()
        compose.onNodeWithTag(TAG_WORDS).assertTextEquals("seven, two, nine, four")

        compose.onNodeWithTag(TAG_INPUT).performTextInput("7294")
        compose.onNodeWithText("OK").performClick()

        assertEquals(1, passed)
    }

    @Test
    fun `a wrong answer stays closed and shows a new number`() {
        showGate()

        compose.onNodeWithTag(TAG_INPUT).performTextInput("1234")
        compose.onNodeWithText("OK").performClick()

        assertEquals(0, passed)
        compose.onNodeWithTag(TAG_WORDS).assertTextEquals("one, zero, zero, eight")
        compose.onNodeWithText("Not quite. Try this one.").assertExists()

        compose.onNodeWithTag(TAG_INPUT).performTextInput("1008")
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, passed)
    }

    @Test
    fun `typing the words instead of digits does not work`() {
        showGate()

        compose.onNodeWithTag(TAG_INPUT).performTextInput("seven two nine four")
        compose.onNodeWithText("OK").performClick()

        assertEquals(0, passed)
    }

    @Test
    fun `cancel dismisses without passing`() {
        showGate()

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(0, passed)
        assertEquals(1, dismissed)
    }
}
