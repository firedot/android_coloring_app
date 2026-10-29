package com.fireworm.colourfun.drawing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.fireworm.colourfun.drawing.brushes.BrushSize
import com.fireworm.colourfun.drawing.brushes.BrushSpec
import com.fireworm.colourfun.drawing.brushes.Medium
import com.fireworm.colourfun.drawing.brushes.StrokePoint
import com.fireworm.colourfun.drawing.document.Document
import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.drawing.history.History
import com.fireworm.colourfun.drawing.regions.RegionMap
import com.fireworm.colourfun.drawing.regions.RegionMaskCache
import kotlin.random.Random
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawingEngineHistoryTest {

    // A small document keeps full-bitmap snapshots cheap; the pipeline is size-independent.
    private val size = DocumentSize(512, 384)
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private val colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.BLACK, Color.YELLOW)
    private var clockMs = 0L

    @Test
    fun `random strokes, undos and redos always leave the expected paint`() {
        repeat(5) { seed -> checkRandomSequence(Random(seed), maxSteps = 10) }
    }

    @Test
    fun `oldest steps are evicted when over the memory budget`() {
        val budget = 60_000L
        val history = History(budgetBytes = budget, maxSteps = 1_000, scope = scope, compressDispatcher = dispatcher)
        val engine = engine(history)
        val random = Random(42)
        val snapshots = mutableListOf(snapshot(engine))

        repeat(STROKES) {
            drawRandomStroke(engine, random)
            dispatcher.scheduler.advanceUntilIdle()
            snapshots += snapshot(engine)
            assertTrue("Over budget: ${history.retainedBytes} bytes", history.retainedBytes <= budget || history.undoCount == 1)
        }

        val kept = history.undoCount
        assertTrue("Expected eviction, kept $kept of $STROKES steps", kept in 1 until STROKES)

        repeat(kept) { engine.undo() }
        assertFalse(engine.canUndo.value)
        assertArrayEquals(
            "Undoing every kept step should land on the oldest kept state",
            snapshots[STROKES - kept],
            snapshot(engine),
        )
    }

    @Test
    fun `a step larger than the whole budget is still kept`() {
        val history = History(budgetBytes = 1L, scope = scope, compressDispatcher = dispatcher)
        val engine = engine(history)
        val blank = snapshot(engine)

        drawRandomStroke(engine, Random(1))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, history.undoCount)
        engine.undo()
        assertArrayEquals(blank, snapshot(engine))
    }

    /** Applies random operations and compares the paint after each one with a model of expected states. */
    private fun checkRandomSequence(random: Random, maxSteps: Int) {
        val engine = engine(History(maxSteps = maxSteps, scope = scope, compressDispatcher = dispatcher))
        // states[current] is what the paint layer should hold; later entries can be redone.
        val states = mutableListOf(snapshot(engine))
        var current = 0

        repeat(OPERATIONS) { step ->
            when (random.nextInt(10)) {
                in 0..4 -> {
                    drawRandomStroke(engine, random)
                    states.subList(current + 1, states.size).clear()
                    states += snapshot(engine)
                    current++
                    if (current > maxSteps) {
                        states.removeAt(0)
                        current--
                    }
                }
                in 5..6 -> {
                    engine.undo()
                    if (current > 0) current--
                }
                in 7..8 -> {
                    engine.redo()
                    if (current < states.lastIndex) current++
                }
                else -> {
                    // A cancelled stroke (second finger) must leave no trace.
                    engine.beginStroke(randomSpec(random), colors.random(random), randomPoint(random), stayInsideLines = random.nextBoolean())
                    engine.extendStroke(List(5) { randomPoint(random) })
                    engine.cancelStroke()
                }
            }
            // Mix compressed and still-uncompressed patches.
            if (random.nextBoolean()) dispatcher.scheduler.advanceUntilIdle()

            assertArrayEquals("Paint differs after step $step", states[current], snapshot(engine))
            assertEquals("canUndo after step $step", current > 0, engine.canUndo.value)
            assertEquals("canRedo after step $step", current < states.lastIndex, engine.canRedo.value)
        }
    }

    private fun engine(history: History): DrawingEngine {
        val outline = createBitmap(size.width, size.height, Bitmap.Config.ALPHA_8)
        val regions = RegionMap.build(outline, outlineWidthPx = 4f)
        return DrawingEngine(Document(size, outline), RegionMaskCache(regions, workDispatcher = dispatcher), history)
    }

    private fun drawRandomStroke(engine: DrawingEngine, random: Random) {
        engine.beginStroke(randomSpec(random), colors.random(random), randomPoint(random), stayInsideLines = random.nextBoolean())
        val points = List(random.nextInt(1, 30)) { randomPoint(random) }
        // Deliver in batches, as touch events with historical points arrive.
        points.chunked(random.nextInt(1, 6)).forEach(engine::extendStroke)
        engine.endStroke()
    }

    private fun randomSpec(random: Random) = BrushSpec(Medium.Pen, BrushSize.entries.random(random))

    private fun randomPoint(random: Random): StrokePoint {
        clockMs += 8
        return StrokePoint(
            x = random.nextFloat() * size.width,
            y = random.nextFloat() * size.height,
            timeMs = clockMs,
        )
    }

    private fun snapshot(engine: DrawingEngine): IntArray {
        val bitmap = engine.document.paint.bitmap
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }

    private companion object {
        const val OPERATIONS = 120
        const val STROKES = 30
    }
}
