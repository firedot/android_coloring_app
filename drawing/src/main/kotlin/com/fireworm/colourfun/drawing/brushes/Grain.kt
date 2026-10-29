package com.fireworm.colourfun.drawing.brushes

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.random.Pcg32
import kotlin.math.pow

/**
 * A tileable paper-grain texture: white with varying alpha. It repeats in document space, so it is
 * fixed to the paper and going over the same spot hits the same "tooth", like real paper.
 *
 * @param floor alpha of the deepest grain, `0..1`; lower means a rougher texture.
 * @param blurPasses 3×3 box blurs; more means coarser, softer grain.
 * @param gamma above 1 pushes more of the texture towards [floor] (more gaps).
 * @param cutoff share of the value range that becomes [floor] outright: empty pits.
 */
internal class Grain(
    size: Int,
    seed: Long,
    floor: Float,
    blurPasses: Int,
    gamma: Float = 1f,
    cutoff: Float = 0f,
) {
    val shader: Shader =
        BitmapShader(texture(size, seed, floor, blurPasses, gamma, cutoff), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    private fun texture(size: Int, seed: Long, floor: Float, blurPasses: Int, gamma: Float, cutoff: Float): Bitmap {
        val random = Pcg32(seed)
        var values = FloatArray(size * size) { random.nextFloat() }
        repeat(blurPasses) { values = blurWrapped(values, size) }

        val min = values.min()
        val range = (values.max() - min).coerceAtLeast(1e-6f)
        val pixels = IntArray(values.size) { i ->
            val normalized = (values[i] - min) / range
            val v = ((normalized - cutoff) / (1f - cutoff)).coerceIn(0f, 1f).pow(gamma)
            val alpha = ((floor + (1f - floor) * v) * 255f).toInt().coerceIn(0, 255)
            (alpha shl 24) or 0xFFFFFF
        }
        return createBitmap(size, size).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
    }

    /** 3×3 box blur that wraps around the edges, so the texture still tiles seamlessly. */
    private fun blurWrapped(src: FloatArray, size: Int): FloatArray = FloatArray(src.size) { i ->
        val x = i % size
        val y = i / size
        var sum = 0f
        for (dy in -1..1) for (dx in -1..1) {
            sum += src[(y + dy + size) % size * size + (x + dx + size) % size]
        }
        sum / 9f
    }
}
