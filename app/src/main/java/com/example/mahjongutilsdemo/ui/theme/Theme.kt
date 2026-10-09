package com.example.mahjongutilsdemo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    secondary = Color(0xFF4A6364),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DDADF),
    secondary = Color(0xFFB0CCCD),
)

@Composable
fun MahjongUtilsDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
