package com.fireworm.colourfun.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.data.library.Difficulty
import com.fireworm.colourfun.data.library.Picture
import com.fireworm.colourfun.data.library.Section
import com.fireworm.colourfun.ui.library.LibraryContent
import com.fireworm.colourfun.ui.library.LibrarySection
import com.fireworm.colourfun.ui.library.LibraryUiState
import com.fireworm.colourfun.ui.library.SavedPicture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class LibraryScreenTest {

    @get:Rule val compose = createComposeRule()

    private val sections = listOf(
        LibrarySection(Section("animals", "paw", 0), listOf(picture("animals/cat_01"), picture("animals/fish_01"))),
        LibrarySection(Section("plants", "leaf", 1), listOf(picture("plants/rose_01"))),
        LibrarySection(Section("various", "star", 2), listOf(picture("various/car_01"))),
    )
    private var state by mutableStateOf(LibraryUiState(sections, saved = emptyList()))
    private val opened = mutableListOf<String>()
    private val continued = mutableListOf<String>()
    private var settingsClicks = 0

    private fun show() = compose.setContent {
        LibraryContent(
            uiState = state,
            loadThumbnail = { null },
            onOpenPicture = { opened += it },
            onContinue = { continued += it },
            onSettings = { settingsClicks++ },
        )
    }

    @Test
    fun `the first section is shown and a picture starts a new artwork`() {
        show()

        compose.onNodeWithContentDescription("Animals").assertIsSelected()
        compose.onAllNodesWithContentDescription(OPEN).assertCountEquals(2)
        compose.onAllNodesWithContentDescription(OPEN).onFirst().performClick()

        assertEquals(listOf("animals/cat_01"), opened)
    }

    @Test
    fun `a section tab shows its pictures`() {
        show()

        compose.onNodeWithContentDescription("Plants").performClick()

        compose.onNodeWithContentDescription("Plants").assertIsSelected()
        compose.onNodeWithContentDescription("Animals").assertIsNotSelected()
        compose.onAllNodesWithContentDescription(OPEN).assertCountEquals(1)
        compose.onNodeWithContentDescription(OPEN).performClick()
        assertEquals(listOf("plants/rose_01"), opened)
    }

    @Test
    fun `my pictures come first and continue the artwork`() {
        state = state.copy(saved = listOf(SavedPicture("a1", File("missing.webp"), 1L), SavedPicture("a2", File("missing.webp"), 2L)))
        show()

        compose.onAllNodesWithContentDescription(CONTINUE).assertCountEquals(2)
        compose.onAllNodesWithContentDescription(CONTINUE).onFirst().performClick()

        assertEquals(listOf("a1"), continued)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `there is no my pictures row before anything is saved`() {
        show()

        compose.onNodeWithContentDescription(CONTINUE).assertDoesNotExist()
    }

    @Test
    fun `thumbnails and tabs are big enough for small fingers`() {
        show()

        compose.onAllNodesWithContentDescription(OPEN).onFirst().assertHeightIsAtLeast(160.dp)
        compose.onNodeWithContentDescription("Things").assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun `settings ask for the parent gate`() {
        show()

        compose.onNodeWithContentDescription("Settings").performClick()

        assertEquals(1, settingsClicks)
    }

    private fun picture(id: String) = Picture(
        id = id,
        section = id.substringBefore('/'),
        file = "$id.png",
        thumb = "${id}_thumb.webp",
        difficulty = Difficulty.Easy,
        regions = 10,
        source = "test",
    )

    private companion object {
        const val OPEN = "Colour this picture"
        const val CONTINUE = "Keep colouring"
    }
}
