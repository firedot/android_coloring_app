package com.fireworm.colourfun.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.data.db.ArtworkDao
import com.fireworm.colourfun.data.db.ArtworkEntity
import com.fireworm.colourfun.data.db.ColourFunDatabase
import com.fireworm.colourfun.data.files.ArtworkFileStore
import com.fireworm.colourfun.drawing.document.DocumentSize
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What to save, captured on the main thread. [paint] must be a private copy; [outline] is the
 * document's outline bitmap and [outlinePng] the library picture, which never change after creation.
 */
class ArtworkSnapshot(
    val id: String,
    val pictureId: String,
    val outlinePng: ByteArray,
    val size: DocumentSize,
    val paperColor: Int,
    val paint: Bitmap,
    val outline: Bitmap,
)

/** A saved artwork read back from disk. [paint] is `null` if it was saved before any painting. */
class StoredArtwork(val entity: ArtworkEntity, val outlinePng: ByteArray, val paint: Bitmap?)

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
            if (existing == null) files.writeOutline(snapshot.id, snapshot.outlinePng)
            files.writePaint(snapshot.id, snapshot.paint)
            files.writeThumbnail(snapshot.id, thumbnail(snapshot))
            val time = now()
            dao.upsert(
                existing?.copy(updatedAt = time) ?: ArtworkEntity(
                    id = snapshot.id,
                    pictureId = snapshot.pictureId,
                    docWidth = snapshot.size.width,
                    docHeight = snapshot.size.height,
                    pictureLeft = snapshot.size.pictureLeft,
                    pictureTop = snapshot.size.pictureTop,
                    documentScale = snapshot.size.scale,
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
            val outline = runCatching { files.readOutline(id) }.getOrNull() ?: return@withContext null
            StoredArtwork(entity, outline, files.readPaint(id))
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

    /** Paper, paint and outline of the picture area (not the side margins), scaled down. */
    private fun thumbnail(snapshot: ArtworkSnapshot): Bitmap {
        val size = snapshot.size
        val source = Rect(size.pictureLeft, size.pictureTop, size.pictureLeft + size.pictureWidth, size.pictureTop + size.pictureHeight)
        val thumb = createBitmap(THUMB_WIDTH, THUMB_WIDTH * source.height() / source.width())
        val canvas = Canvas(thumb)
        canvas.drawColor(snapshot.paperColor)
        val target = Rect(0, 0, thumb.width, thumb.height)
        val filter = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(snapshot.paint, source, target, filter)
        canvas.drawBitmap(snapshot.outline, source, target, filter)
        return thumb
    }

    companion object {
        /** The same size as the library thumbnails. */
        private const val THUMB_WIDTH = 384

        /** The app's repository: a Room database plus `filesDir/artworks/`. */
        fun create(context: Context): ArtworkRepository {
            val root = File(context.filesDir, "artworks")
            val database = ColourFunDatabase.create(context, onDestructiveMigration = { root.deleteRecursively() })
            return ArtworkRepository(database.artworkDao(), ArtworkFileStore(root))
        }
    }
}
