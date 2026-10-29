package com.fireworm.colourfun.domain

import com.fireworm.colourfun.data.artwork.ArtworkRepository
import com.fireworm.colourfun.drawing.DrawingConfig
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Restores a saved artwork from its files; never regenerates it from the seed. */
class LoadPictureUseCase @Inject constructor(
    private val repository: ArtworkRepository,
    private val config: DrawingConfig,
) {
    /** `null` if the artwork no longer exists. */
    suspend operator fun invoke(id: String): Picture? {
        val stored = repository.load(id) ?: return null
        return withContext(Dispatchers.Default) { prepare(stored.lineArt, config, stored.paint) }
    }
}
