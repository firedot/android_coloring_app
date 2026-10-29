package com.fireworm.colourfun.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fireworm.colourfun.data.artwork.ArtworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [updatedAt] changes with every save, so the thumbnail reloads. */
data class GalleryItem(val id: String, val thumbnail: File, val updatedAt: Long)

@HiltViewModel
class GalleryViewModel @Inject constructor(repository: ArtworkRepository) : ViewModel() {

    /** Newest first; `null` while loading. */
    val items: StateFlow<List<GalleryItem>?> = repository.observeAll()
        .map { artworks -> artworks.map { GalleryItem(it.id, repository.thumbnailFile(it.id), it.updatedAt) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
