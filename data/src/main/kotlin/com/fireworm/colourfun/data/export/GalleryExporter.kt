package com.fireworm.colourfun.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves a finished picture as a PNG in the device's photos, under `Pictures/Colour Fun/`.
 *
 * API 29+ goes through MediaStore and needs no permission; the entry stays pending until the file
 * is complete. API 26–28 writes the file directly and needs WRITE_EXTERNAL_STORAGE, which the app
 * asks for only after the parent gate.
 */
class GalleryExporter(private val context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) {

    /** The new image's uri. Throws [IOException] if it could not be written. */
    suspend fun export(image: Bitmap, displayName: String): Uri = withContext(io) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportToMediaStore(image, displayName) else exportToFile(image, displayName)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(image: Bitmap, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused $displayName")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri")
            stream.use { if (!image.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG encoding failed") }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun exportToFile(image: Bitmap, displayName: String): Uri {
        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Cannot create $folder")
        val file = File(folder, displayName)
        try {
            file.outputStream().buffered().use { if (!image.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG encoding failed") }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        // Make it show up in photo apps.
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(MIME_PNG), null)
        return Uri.fromFile(file)
    }

    private companion object {
        const val FOLDER = "Colour Fun"
        const val MIME_PNG = "image/png"
    }
}
