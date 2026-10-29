package com.fireworm.colourfun.domain

import android.content.Context
import android.graphics.Bitmap
import com.fireworm.colourfun.drawing.DrawingConfig
import com.fireworm.colourfun.drawing.layers.OutlineRasterizer
import com.fireworm.colourfun.drawing.regions.RegionMap
import com.fireworm.colourfun.generator.AnimalGenerator
import com.fireworm.colourfun.generator.LineArt
import com.fireworm.colourfun.generator.parts.PartLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A freshly generated picture, ready to colour. */
class NewPicture(val lineArt: LineArt, val outline: Bitmap, val regions: RegionMap)

/** Generates a new animal, rasterizes its outline and labels its regions, off the main thread. */
@Singleton
class NewPictureUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: DrawingConfig,
) {
    /** Parts are read from assets once, on first use. */
    private val generator by lazy { AnimalGenerator(PartLibrary.parse(readParts())) }

    suspend operator fun invoke(seed: Long = Random.nextLong()): NewPicture = withContext(Dispatchers.Default) {
        val lineArt = generator.generate(seed)
        val size = config.documentSize
        val outline = OutlineRasterizer.rasterize(lineArt, size)
        NewPicture(lineArt, outline, RegionMap.build(outline, outlineWidthPx = lineArt.strokeWidth * size.scale))
    }

    private fun readParts(): List<String> {
        val assets = context.assets
        return assets.list(PARTS_DIR).orEmpty().filter { it.endsWith(".json") }.sorted().map { name ->
            assets.open("$PARTS_DIR/$name").bufferedReader().use { it.readText() }
        }
    }

    private companion object {
        const val PARTS_DIR = "parts"
    }
}
