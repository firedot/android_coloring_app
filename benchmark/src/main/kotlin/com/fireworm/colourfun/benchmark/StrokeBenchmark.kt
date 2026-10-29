package com.fireworm.colourfun.benchmark

import android.graphics.Point
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Draws wavy strokes across the picture and measures:
 * - frame time while drawing ([FrameTimingMetric]);
 * - the engine's work per touch batch and per finished stroke (trace sections in DrawingEngine).
 *
 * Numbers from an emulator are meaningless; run on a device.
 */
@RunWith(AndroidJUnit4::class)
class StrokeBenchmark {

    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun penStrokes() = measureStrokes(tool = "Pen")

    @Test
    fun sprayStrokes() = measureStrokes(tool = "Spray")

    @OptIn(ExperimentalMetricApi::class)
    private fun measureStrokes(tool: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(EXTEND_STROKE, TraceSectionMetric.Mode.Average),
            TraceSectionMetric(EXTEND_STROKE, TraceSectionMetric.Mode.Max),
            TraceSectionMetric(END_STROKE, TraceSectionMetric.Mode.Average),
        ),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForPicture()
            device.findObject(By.desc(tool))?.click()
        },
    ) {
        repeat(STROKES) { drawWave(it) }
    }

    /** The new-animal button is disabled while the picture is generated. */
    private fun MacrobenchmarkScope.waitForPicture() {
        device.wait(Until.hasObject(By.desc("New animal").enabled(true)), 15_000)
    }

    /** A sine wave across the middle of the screen, where the picture is in either orientation. */
    private fun MacrobenchmarkScope.drawWave(index: Int) {
        val width = device.displayWidth
        val height = device.displayHeight
        val centreY = height / 2 + (index - STROKES / 2) * height / 20
        val points = Array(POINTS) { i ->
            val t = i / (POINTS - 1f)
            Point(
                (width * (0.3f + 0.4f * t)).toInt(),
                (centreY + sin(t * 4 * PI) * height / 16).toInt(),
            )
        }
        // Each segment is split into steps of ~5 ms, roughly a finger's touch rate.
        device.swipe(points, SEGMENT_STEPS)
    }

    private companion object {
        const val PACKAGE = "com.fireworm.colourfun"

        // Same names as DrawingEngine.TRACE_*; this test APK cannot depend on :drawing.
        const val EXTEND_STROKE = "DrawingEngine.extendStroke"
        const val END_STROKE = "DrawingEngine.endStroke"

        const val STROKES = 6
        const val POINTS = 24
        const val SEGMENT_STEPS = 4
    }
}
