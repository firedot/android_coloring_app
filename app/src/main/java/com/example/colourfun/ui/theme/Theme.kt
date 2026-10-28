package com.example.colourfun.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColourFunColors = lightColorScheme(
    primary = Color(0xFFE53935),
    secondary = Color(0xFF1E88E5),
    tertiary = Color(0xFFFDD835),
    background = Color.White,
    surface = Color.White,
)

@Composable
fun ColourFunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColourFunColors, content = content)
}
