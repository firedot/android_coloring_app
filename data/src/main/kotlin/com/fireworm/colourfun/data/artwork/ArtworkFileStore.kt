package com.fireworm.colourfun.data.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.fireworm.colourfun.generator.LineArt
import com.fireworm.colourfun.generator.LineArtCodec
import java.io.File
import java.io.IOException

/**
 * The files of each artwork: `<root>/<id>/outline.json`, `paint.png` and `thumb.webp`.
 * Every write goes to a temporary file first and is renamed into place, so a kill mid-save leaves
 * the previous version intact. Blocking I/O: call off the main thread.
 */
class ArtworkFileStore(private val root: File) {

    fun directory(id: String) = File(root, id)
    fun thumbnailFile(id: String) = File(directory(id), THUMBNAIL)

    fun writeOutline(id: String, lineArt: LineArt) = atomicWrite(File(directory(id), OUTLINE)) { file ->
        file.writeText(LineArtCodec.encode(lineArt))
    }

    fun readOutline(id: String): LineArt = LineArtCodec.decode(File(directory(id), OUTLINE).readText())

    /** PNG is lossless, so restored paint is pixel-identical to what was saved. */
    fun writePaint(id: String, paint: Bitmap) = atomicWrite(File(directory(id), PAINT)) { file ->
        file.outputStream().buffered().use { check(paint.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed" } }
    }

    /** The saved paint as a mutable bitmap, or `null` if the artwork has never been painted. */
    fun readPaint(id: String): Bitmap? {
        val file = File(directory(id), PAINT)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Cannot decode $file")
    }

    fun writeThumbnail(id: String, thumbnail: Bitmap) = atomicWrite(thumbnailFile(id)) { file ->
        file.outputStream().buffered().use { thumbnail.compress(webp, THUMBNAIL_QUALITY, it) }
    }

    fun delete(id: String) {
        directory(id).deleteRecursively()
    }

    private inline fun atomicWrite(target: File, write: (File) -> Unit) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        write(temp)
        if (!temp.renameTo(target)) {
            // Windows (tests) cannot rename over an existing file.
            target.delete()
            if (!temp.renameTo(target)) throw IOException("Cannot replace $target")
        }
    }

    private companion object {
        const val OUTLINE = "outline.json"
        const val PAINT = "paint.png"
        const val THUMBNAIL = "thumb.webp"
        const val THUMBNAIL_QUALITY = 85

        val webp: Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
    }
}
