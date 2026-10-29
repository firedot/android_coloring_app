package com.fireworm.colourfun.domain

import com.fireworm.colourfun.data.library.PictureRepository
import com.fireworm.colourfun.drawing.DrawingConfig
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Starts a new artwork from a library picture, in a document sized to the screen. Nothing is saved
 * until the child paints, so untouched pictures never reach "my pictures".
 */
class NewArtworkUseCase @Inject constructor(
    private val pictures: PictureRepository,
    private val config: DrawingConfig,
    private val screen: ScreenMetrics,
) {
    suspend operator fun invoke(pictureId: String): PreparedArtwork = withContext(Dispatchers.Default) {
        val png = pictures.outlinePng(pictureId)
        val (width, height) = screen.size()
        prepare(pictureId, png, config.documentSize(width, height))
    }
}
