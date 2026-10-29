package com.example.colourfun.debug

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.colourfun.BuildConfig
import java.util.Locale

/**
 * Debug only. Logs the timing of every frame rendered while a stroke is in progress, then a summary
 * when it ends. Filter logcat with `-s FrameTime`.
 *
 * "sync" is the render-thread stage that uploads changed bitmaps as GPU textures, so it shows the
 * cost of re-uploading the full-size stroke layer every frame. "dirty" is how much of the document
 * actually changed, which is what a tiled upload would send instead.
 */
class FrameTimeLogger(private val window: Window) {
    private val thread = HandlerThread("FrameTimeLogger").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var active = false

    // Only touched on the logger thread.
    private val totals = ArrayList<Float>()
    private val syncs = ArrayList<Float>()

    // Only touched on the main thread.
    private var draws = 0
    private var dirtySum = 0f

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (active) record(metrics)
    }

    fun attach() {
        window.addOnFrameMetricsAvailableListener(listener, handler)
    }

    fun detach() {
        window.removeOnFrameMetricsAvailableListener(listener)
        thread.quitSafely()
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) {
            draws = 0
            dirtySum = 0f
            handler.post {
                totals.clear()
                syncs.clear()
            }
        } else {
            val averageDirty = if (draws > 0) dirtySum / draws else 0f
            handler.post { logSummary(averageDirty) }
        }
    }

    fun onFrameDrawn(dirtyFraction: Float) {
        if (!active) return
        draws++
        dirtySum += dirtyFraction
    }

    private fun record(metrics: FrameMetrics) {
        val total = metrics.ms(FrameMetrics.TOTAL_DURATION)
        val sync = metrics.ms(FrameMetrics.SYNC_DURATION)
        totals += total
        syncs += sync
        Log.d(
            TAG,
            "frame total=%.1f ms  draw=%.1f  sync/upload=%.1f  issue=%.1f  swap=%.1f".format(
                Locale.US,
                total,
                metrics.ms(FrameMetrics.DRAW_DURATION),
                sync,
                metrics.ms(FrameMetrics.COMMAND_ISSUE_DURATION),
                metrics.ms(FrameMetrics.SWAP_BUFFERS_DURATION),
            ),
        )
    }

    private fun logSummary(averageDirty: Float) {
        if (totals.isEmpty()) return
        val frameBudgetMs = 1000f / (window.decorView.display?.refreshRate ?: 60f)
        val sorted = totals.sorted()
        Log.i(
            TAG,
            ("stroke: %d frames  total avg=%.1f p90=%.1f max=%.1f ms  sync avg=%.1f max=%.1f ms  " +
                "over %.1f ms budget=%d  avg dirty=%.1f%% of document").format(
                Locale.US,
                totals.size,
                totals.average(),
                sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.lastIndex)],
                sorted.last(),
                syncs.average(),
                syncs.max(),
                frameBudgetMs,
                totals.count { it > frameBudgetMs },
                averageDirty * 100,
            ),
        )
    }

    private fun FrameMetrics.ms(id: Int): Float = getMetric(id) / 1_000_000f

    private companion object {
        const val TAG = "FrameTime"
    }
}

/** A logger attached to the current window in debug builds, or `null` in release builds. */
@Composable
fun rememberFrameTimeLogger(): FrameTimeLogger? {
    if (!BuildConfig.DEBUG) return null
    val window = LocalActivity.current?.window ?: return null
    val logger = remember(window) { FrameTimeLogger(window) }
    DisposableEffect(logger) {
        logger.attach()
        onDispose { logger.detach() }
    }
    return logger
}
