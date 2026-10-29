package com.fireworm.colourfun.domain

import android.net.Uri
import com.fireworm.colourfun.data.ArtworkRepository
import com.fireworm.colourfun.data.export.GalleryExporter
import com.fireworm.colourfun.drawing.document.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Flattens paper + paint + outline, cropped to the picture rectangle, into the device's photos. */
class ExportToGalleryUseCase @Inject constructor(
    private val exporter: GalleryExporter,
    private val repository: ArtworkRepository,
) {
    /** Call on the main thread (the document belongs to it); the file is written in the background. */
    suspend operator fun invoke(document: Document, artworkId: String?): Uri {
        val image = document.flattenPicture()
        val name = "colour-fun-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.png"
        val uri = exporter.export(image, name)
        artworkId?.let { repository.setExportedUri(it, uri.toString()) }
        return uri
    }
}
