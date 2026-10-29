package com.fireworm.colourfun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
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
        enableEdgeToEdge()
        setContent {
            ColourFunTheme {
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    ColourFunNavHost()
                }
            }
        }
    }
}
