package com.fireworm.colourfun.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.data.artwork.ArtworkSnapshot
import com.fireworm.colourfun.generator.LineArt
import com.fireworm.colourfun.generator.OutlineShape
import com.fireworm.colourfun.generator.geometry.PathShape
import com.fireworm.colourfun.generator.geometry.Vec2
import kotlin.random.Random

/** Paint with opaque, translucent and nearly transparent pixels: the cases PNG must keep exact. */
fun testPaint(width: Int, height: Int, seed: Int = 3): Bitmap {
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val random = Random(seed)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(60) {
        paint.color = Color.argb(random.nextInt(1, 256), random.nextInt(256), random.nextInt(256), random.nextInt(256))
        canvas.drawCircle(random.nextFloat() * width, random.nextFloat() * height, 4f + random.nextFloat() * width / 6f, paint)
    }
    for (alpha in 1..32) bitmap.setPixel(alpha, 0, Color.argb(alpha, 200, 100, 50))
    return bitmap
}

fun testLineArt(seed: Long = 9) = LineArt(
    width = 2048,
    height = 1536,
    shapes = listOf(OutlineShape(PathShape.ellipse(Vec2(1024f, 768f), 500f, 400f))),
    strokeWidth = 12f,
    seed = seed,
    generatorVersion = 1,
)

fun testSnapshot(id: String, paint: Bitmap) = ArtworkSnapshot(
    id = id,
    lineArt = testLineArt(),
    documentScale = paint.width / 2048f,
    paperColor = Color.WHITE,
    paint = paint,
    outline = createBitmap(paint.width, paint.height),
)

fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
