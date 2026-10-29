package com.fireworm.colourfun.domain

import android.content.Context
import com.fireworm.colourfun.drawing.DrawingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Starts a new artwork from a library picture, in a document sized to the screen. Nothing is saved
 * until the child paints, so untouched pictures never reach "my pictures".
 */
class NewArtworkUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: DrawingConfig,
    private val screen: ScreenMetrics,
) {
    suspend operator fun invoke(pictureId: String): PreparedArtwork = withContext(Dispatchers.Default) {
        val png = context.assets.open("$PICTURES_DIR/$pictureId.png").use { it.readBytes() }
        val (width, height) = screen.size()
        prepare(pictureId, png, config.documentSize(width, height))
    }

    private companion object {
        const val PICTURES_DIR = "pictures"
    }
}
