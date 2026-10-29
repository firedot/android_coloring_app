package com.fireworm.colourfun.domain

import android.graphics.Bitmap
import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.layers.OutlineLayer
import com.fireworm.colourfun.drawing.regions.RegionMap

/**
 * An artwork ready to colour: the library picture it comes from, its outline placed in a document
 * of [size], labelled regions and, if restored, its paint.
 */
class PreparedArtwork(
    val pictureId: String,
    val outlinePng: ByteArray,
    val size: DocumentSize,
    val outline: Bitmap,
    val regions: RegionMap,
    val paint: Bitmap? = null,
)

/** Decodes and labels the picture. CPU heavy: call on a background dispatcher. */
internal fun prepare(pictureId: String, outlinePng: ByteArray, size: DocumentSize, paint: Bitmap? = null): PreparedArtwork {
    val outline = OutlineLayer.decode(outlinePng, size)
    val regions = RegionMap.build(outline, outlineWidthPx = OutlineLayer.MAX_LINE_WIDTH * size.scale)
    return PreparedArtwork(pictureId, outlinePng, size, outline, regions, paint)
}
