package com.fireworm.colourfun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fireworm.colourfun.ui.canvas.ColoringScreen
import com.fireworm.colourfun.ui.theme.ColourFunTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColourFunTheme {
                ColoringScreen()
            }
        }
    }
}
