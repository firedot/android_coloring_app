package com.fireworm.colourfun.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.generator.LineArt
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What to save, captured on the main thread. [paint] must be a private copy; [outline] is the
 * document's outline bitmap, which never changes after creation.
 */
class ArtworkSnapshot(
    val id: String,
    val lineArt: LineArt,
    val documentScale: Float,
    val paperColor: Int,
    val paint: Bitmap,
    val outline: Bitmap,
)

/** A saved artwork read back from disk. [paint] is `null` if it was saved before any painting. */
class StoredArtwork(val entity: ArtworkEntity, val lineArt: LineArt, val paint: Bitmap?)

class ArtworkRepository(
    private val dao: ArtworkDao,
    private val files: ArtworkFileStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** One save at a time, so files and rows never interleave. */
    private val saving = Mutex()

    fun observeAll(): Flow<List<ArtworkEntity>> = dao.observeAll()

    fun thumbnailFile(id: String) = files.thumbnailFile(id)

    suspend fun save(snapshot: ArtworkSnapshot) = withContext(io) {
        saving.withLock {
            val existing = dao.get(snapshot.id)
            // The outline never changes, so it is written once.
            if (existing == null) files.writeOutline(snapshot.id, snapshot.lineArt)
            files.writePaint(snapshot.id, snapshot.paint)
            files.writeThumbnail(snapshot.id, thumbnail(snapshot))
            val time = now()
            dao.upsert(
                existing?.copy(updatedAt = time) ?: ArtworkEntity(
                    id = snapshot.id,
                    seed = snapshot.lineArt.seed,
                    generatorVersion = snapshot.lineArt.generatorVersion,
                    documentScale = snapshot.documentScale,
                    createdAt = time,
                    updatedAt = time,
                ),
            )
        }
    }

    /** `null` if there is no such artwork or its files are gone. */
    suspend fun load(id: String): StoredArtwork? = withContext(io) {
        saving.withLock {
            val entity = dao.get(id) ?: return@withContext null
            val lineArt = runCatching { files.readOutline(id) }.getOrNull() ?: return@withContext null
            StoredArtwork(entity, lineArt, files.readPaint(id))
        }
    }

    /** Waits for pending saves, so the row of a just-saved picture exists. */
    suspend fun setExportedUri(id: String, uri: String) = withContext(io) {
        saving.withLock { dao.setExportedUri(id, uri) }
    }

    suspend fun delete(id: String) = withContext(io) {
        saving.withLock {
            dao.delete(id)
            files.delete(id)
        }
    }

    /** Paper, paint and outline, flattened and scaled down for the gallery. */
    private fun thumbnail(snapshot: ArtworkSnapshot): Bitmap {
        val thumb = createBitmap(THUMB_WIDTH, THUMB_WIDTH * snapshot.paint.height / snapshot.paint.width)
        val canvas = Canvas(thumb)
        canvas.drawColor(snapshot.paperColor)
        val target = Rect(0, 0, thumb.width, thumb.height)
        val filter = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(snapshot.paint, null, target, filter)
        canvas.drawBitmap(snapshot.outline, null, target, filter)
        return thumb
    }

    companion object {
        private const val THUMB_WIDTH = 400

        /** The app's repository: a Room database plus `filesDir/artworks/`. */
        fun create(context: Context) = ArtworkRepository(
            ArtworkDatabase.create(context).artworkDao(),
            ArtworkFileStore(File(context.filesDir, "artworks")),
        )
    }
}
