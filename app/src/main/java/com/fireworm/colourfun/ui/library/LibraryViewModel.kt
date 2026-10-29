package com.fireworm.colourfun.ui.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fireworm.colourfun.data.ArtworkRepository
import com.fireworm.colourfun.data.library.Picture
import com.fireworm.colourfun.data.library.PictureRepository
import com.fireworm.colourfun.data.library.Section
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** A saved artwork in "my pictures". [updatedAt] changes with every save, so the thumbnail reloads. */
data class SavedPicture(val id: String, val thumbnail: File, val updatedAt: Long)

data class LibrarySection(val section: Section, val pictures: List<Picture>)

/** `null` lists while loading. */
data class LibraryUiState(val sections: List<LibrarySection>? = null, val saved: List<SavedPicture>? = null)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val pictures: PictureRepository,
    artworks: ArtworkRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        flow { emit(pictures.catalog().bySection().map { (section, list) -> LibrarySection(section, list) }) },
        artworks.observeAll(),
    ) { sections, saved ->
        LibraryUiState(sections, saved.map { SavedPicture(it.id, artworks.thumbnailFile(it.id), it.updatedAt) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    suspend fun thumbnail(picture: Picture): ImageBitmap? = pictures.thumbnail(picture)?.asImageBitmap()
}
