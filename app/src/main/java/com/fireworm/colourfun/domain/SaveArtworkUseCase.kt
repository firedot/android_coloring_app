package com.fireworm.colourfun.domain

import android.graphics.Bitmap
import android.util.Log
import com.fireworm.colourfun.data.artwork.ArtworkRepository
import com.fireworm.colourfun.data.artwork.ArtworkSnapshot
import com.fireworm.colourfun.di.ApplicationScope
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.generator.LineArt
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Saves the picture. The paint is copied right away on the main thread (the document belongs to
 * it); the files are written in the application scope, so leaving the screen cannot cancel a save.
 */
class SaveArtworkUseCase @Inject constructor(
    private val repository: ArtworkRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    operator fun invoke(id: String, lineArt: LineArt, document: Document): Job {
        val snapshot = ArtworkSnapshot(
            id = id,
            lineArt = lineArt,
            documentScale = document.size.scale,
            paperColor = document.paper.color,
            paint = document.paint.bitmap.copy(Bitmap.Config.ARGB_8888, false),
            outline = document.outline.bitmap,
        )
        return scope.launch {
            // A failed save must never crash the child's app; the next save tries again.
            runCatching { repository.save(snapshot) }.onFailure { Log.w(TAG, "Saving $id failed", it) }
        }
    }

    private companion object {
        const val TAG = "SaveArtwork"
    }
}
