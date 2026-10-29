package com.fireworm.colourfun.domain

import com.fireworm.colourfun.data.ArtworkRepository
import com.fireworm.colourfun.data.db.documentSize
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Restores a saved artwork from its files, at the document size it was made at. */
class LoadArtworkUseCase @Inject constructor(private val repository: ArtworkRepository) {
    /** `null` if the artwork no longer exists. */
    suspend operator fun invoke(id: String): PreparedArtwork? {
        val stored = repository.load(id) ?: return null
        return withContext(Dispatchers.Default) {
            prepare(stored.entity.pictureId, stored.outlinePng, stored.entity.documentSize, stored.paint)
        }
    }
}
