package com.fireworm.colourfun.drawing.regions

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-document ALPHA_8 masks of dilated regions (0xFF inside, 0 outside), keeping the
 * [capacity] most recently used. Main thread only.
 */
class RegionMaskCache(
    val regions: RegionMap,
    private val capacity: Int = 2,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val masks = LinkedHashMap<Int, Bitmap>(capacity + 1, 1f, true)

    /** The mask for [regionId], built synchronously if it is not cached (a bounding-box scan). */
    fun get(regionId: Int): Bitmap = masks[regionId] ?: build(regionId).also { put(regionId, it) }

    /** Like [get], but builds the mask off the main thread. */
    suspend fun load(regionId: Int): Bitmap =
        masks[regionId] ?: withContext(workDispatcher) { build(regionId) }.also { put(regionId, it) }

    private fun put(regionId: Int, mask: Bitmap) {
        masks[regionId] = mask
        while (masks.size > capacity) masks.remove(masks.keys.first())
    }

    private fun build(regionId: Int): Bitmap {
        val mask = createBitmap(regions.width, regions.height, Bitmap.Config.ALPHA_8)
        val bytes = ByteArray(mask.rowBytes * mask.height)
        regions.writeMask(regionId, bytes, mask.rowBytes)
        mask.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return mask
    }
}
