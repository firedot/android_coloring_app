package com.fireworm.colourfun.drawing.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.DrawingEngine
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.regions.RegionMap
import com.fireworm.colourfun.drawing.regions.RegionMaskCache
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** A fixed S-shaped stroke on a blank document strip, drawn at full (2048 px wide) scale. */
internal object FixedStroke {
    const val SEED = 42L
    const val WIDTH = 640
    const val HEIGHT = 240

    /** Full base width so brush sizes are at their real scale; only the left part is used. */
    private val size = DocumentSize(DocumentSize.BASE_WIDTH, HEIGHT)

    /**
     * 64 points 8 ms apart along one S-curve, then the finger rests on the last point for a few
     * frames before lifting, so the smoothed stroke settles where it ends.
     */
    val points: List<StrokePoint> = List(64) { i ->
        val t = i / 63f
        StrokePoint(x = 40f + t * 560f, y = HEIGHT / 2f + 70f * sin(t * 2f * PI.toFloat()), timeMs = i * 8L)
    }.let { curve -> curve + List(12) { curve.last().copy(timeMs = curve.last().timeMs + (it + 1) * 8L) } }

    fun engine(): DrawingEngine {
        val dispatcher = StandardTestDispatcher()
        val outline = createBitmap(size.width, size.height, Bitmap.Config.ALPHA_8)
        val regions = RegionMap.build(outline, outlineWidthPx = 4f)
        val history = History(scope = TestScope(dispatcher), compressDispatcher = dispatcher)
        return DrawingEngine(Document(size, outline), RegionMaskCache(regions, workDispatcher = dispatcher), history)
    }

    /**
     * Starts the stroke and delivers every point in batches, like touch events, with a frame tick
     * after each batch (which only the spray uses). Does not end the stroke.
     */
    fun draw(engine: DrawingEngine, spec: BrushSpec, color: Int) {
        engine.beginStroke(spec, color, points.first(), stayInsideLines = false, seed = SEED)
        points.drop(1).chunked(4).forEach { batch ->
            engine.extendStroke(batch)
            engine.tick(batch.last().timeMs + 4)
        }
    }

    /** Opaque and semi-transparent bands of paint, for brushes that erase or pick up colour. */
    fun paintBands(engine: DrawingEngine) {
        val paint = Paint()
        listOf(0xFFFFC107, 0x80E91E63, 0xFF4CAF50, 0x4003A9F4).forEachIndexed { i, color ->
            paint.color = color.toInt()
            engine.document.paint.canvas.drawRect(i * 160f, 0f, (i + 1) * 160f - 80f, HEIGHT.toFloat(), paint)
        }
    }

    /** The picture as the child sees it, cropped to the stroke area. */
    fun render(engine: DrawingEngine): Bitmap = createBitmap(WIDTH, HEIGHT).also { engine.draw(Canvas(it)) }
}
