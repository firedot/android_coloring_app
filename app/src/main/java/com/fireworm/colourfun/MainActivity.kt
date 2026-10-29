package com.fireworm.colourfun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fireworm.colourfun.ui.feedback.Feedback
import com.fireworm.colourfun.ui.feedback.LocalFeedback
import com.fireworm.colourfun.ui.nav.ColourFunNavHost
import com.fireworm.colourfun.ui.theme.ColourFunTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var feedback: Feedback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Drawn under the system bars and display cutouts; the bars stay hidden (immersive).
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            ColourFunTheme {
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    ColourFunNavHost()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and other apps can bring the bars back; hide them again on return.
        if (hasFocus) hideSystemBars()
    }

    /** A swipe from the edge shows the bars briefly; they hide again by themselves. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
