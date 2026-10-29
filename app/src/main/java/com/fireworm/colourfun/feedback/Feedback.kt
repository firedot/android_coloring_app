package com.fireworm.colourfun.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import com.fireworm.colourfun.R
import com.fireworm.colourfun.data.settings.Settings
import com.fireworm.colourfun.data.settings.SettingsRepository
import com.fireworm.colourfun.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

enum class Sound {
    /** Any button. */
    Tap,

    /** The fill bucket pouring paint. */
    Fill,

    /** Sliding along the palette: a quiet tick, at most every [Feedback.TICK_INTERVAL_MS]. */
    Tick,
}

/** A short sound and a light haptic on every tap, each switched off by its parent setting. */
@Singleton
class Feedback @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val current = settings.settings.stateIn(scope, SharingStarted.Eagerly, Settings())

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // Decoded in the background; play() before that is silently skipped.
    private val tapId = soundPool.load(context, R.raw.tap, 1)
    private val fillId = soundPool.load(context, R.raw.fill, 1)

    private var lastTickMs = 0L

    fun play(sound: Sound, view: View) {
        if (sound == Sound.Tick) {
            val now = SystemClock.uptimeMillis()
            if (now - lastTickMs < TICK_INTERVAL_MS) return
            lastTickMs = now
        }
        val settings = current.value
        if (settings.soundEnabled) {
            val (id, volume) = when (sound) {
                Sound.Tap -> tapId to TAP_VOLUME
                Sound.Fill -> fillId to TAP_VOLUME
                Sound.Tick -> tapId to TICK_VOLUME
            }
            soundPool.play(id, volume, volume, 1, 0, 1f)
        }
        if (settings.hapticsEnabled) {
            view.performHapticFeedback(if (sound == Sound.Tick) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    companion object {
        const val TICK_INTERVAL_MS = 150L
        private const val MAX_STREAMS = 4
        private const val TAP_VOLUME = 0.6f
        private const val TICK_VOLUME = 0.25f
    }
}

/** `null` in previews and tests, where taps stay silent. */
val LocalFeedback = staticCompositionLocalOf<Feedback?> { null }

/** Plays [Sound]s with haptics on the current view. */
@Composable
fun rememberFeedback(): (Sound) -> Unit {
    val feedback = LocalFeedback.current
    val view = LocalView.current
    return remember(feedback, view) { { sound -> feedback?.play(sound, view) } }
}
