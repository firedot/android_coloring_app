package com.fireworm.colourfun.data.library

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The bundled picture library under `assets/pictures/`. The folders are listed once. */
class PictureRepository(private val assets: AssetManager) {

    private val mutex = Mutex()
    private var library: PictureLibrary? = null

    suspend fun library(): PictureLibrary = mutex.withLock {
        library ?: withContext(Dispatchers.IO) {
            PictureLibrary.scan { path -> assets.list(if (path.isEmpty()) DIR else "$DIR/$path").orEmpty().toList() }
        }.also { library = it }
    }

    /** The picture's line-art PNG; throws if there is no picture [pictureId]. */
    suspend fun outlinePng(pictureId: String): ByteArray {
        val picture = requireNotNull(library().picture(pictureId)) { "No picture $pictureId" }
        return withContext(Dispatchers.IO) { assets.open("$DIR/${picture.file}").use { it.readBytes() } }
    }

    suspend fun thumbnail(picture: Picture): Bitmap? = withContext(Dispatchers.IO) {
        assets.open("$DIR/${picture.thumb}").use(BitmapFactory::decodeStream)
    }

    private companion object {
        const val DIR = "pictures"
    }
}
