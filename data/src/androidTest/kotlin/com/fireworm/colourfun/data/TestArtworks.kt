package com.fireworm.colourfun.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.document.DocumentSize
import java.io.ByteArrayOutputStream
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

/** Stands in for a library picture PNG; its content doesn't matter to the repository. */
val testOutlinePng: ByteArray by lazy {
    val picture = createBitmap(8, 6).apply { eraseColor(Color.WHITE) }
    ByteArrayOutputStream().use {
        picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }
}

/** A document the size of [paint]; [scale] makes the picture area fit its height. */
fun testSnapshot(id: String, paint: Bitmap, scale: Float = paint.height / DocumentSize.PICTURE_HEIGHT.toFloat()) = ArtworkSnapshot(
    id = id,
    pictureId = "animals/test_01",
    outlinePng = testOutlinePng,
    size = DocumentSize(paint.width, paint.height, scale),
    paperColor = Color.WHITE,
    paint = paint,
    outline = createBitmap(paint.width, paint.height, Bitmap.Config.ALPHA_8),
)

fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
