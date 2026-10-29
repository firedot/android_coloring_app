package com.fireworm.colourfun.domain

import android.graphics.Bitmap
import android.util.Log
import com.fireworm.colourfun.data.ArtworkRepository
import com.fireworm.colourfun.data.ArtworkSnapshot
import com.fireworm.colourfun.di.ApplicationScope
import com.fireworm.colourfun.drawing.document.Document
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Saves the artwork. The paint is copied right away on the main thread (the document belongs to
 * it); the files are written in the application scope, so leaving the screen cannot cancel a save.
 */
class AutosaveUseCase @Inject constructor(
    private val repository: ArtworkRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    operator fun invoke(id: String, artwork: PreparedArtwork, document: Document): Job {
        val snapshot = ArtworkSnapshot(
            id = id,
            pictureId = artwork.pictureId,
            outlinePng = artwork.outlinePng,
            size = document.size,
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
        const val TAG = "Autosave"
    }
}
