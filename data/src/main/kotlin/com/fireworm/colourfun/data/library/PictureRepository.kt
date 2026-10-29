package com.fireworm.colourfun.data.library

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The bundled picture library under `assets/pictures/`. The catalog is read once. */
class PictureRepository(private val assets: AssetManager) {

    private val mutex = Mutex()
    private var catalog: PictureCatalog? = null

    suspend fun catalog(): PictureCatalog = mutex.withLock {
        catalog ?: withContext(Dispatchers.IO) {
            PictureCatalog.parse(assets.open("$DIR/catalog.json").bufferedReader().use { it.readText() })
        }.also { catalog = it }
    }

    /** The picture's line-art PNG; throws if [pictureId] is not in the catalog. */
    suspend fun outlinePng(pictureId: String): ByteArray {
        val picture = requireNotNull(catalog().picture(pictureId)) { "No picture $pictureId" }
        return withContext(Dispatchers.IO) { assets.open("$DIR/${picture.file}").use { it.readBytes() } }
    }

    suspend fun thumbnail(picture: Picture): Bitmap? = withContext(Dispatchers.IO) {
        assets.open("$DIR/${picture.thumb}").use(BitmapFactory::decodeStream)
    }

    private companion object {
        const val DIR = "pictures"
    }
}
