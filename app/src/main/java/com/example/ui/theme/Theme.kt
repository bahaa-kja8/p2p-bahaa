package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldColor,
    onPrimary = Color.Black,
    secondary = CardColor,
    onSecondary = TextColor,
    background = BgColor,
    onBackground = TextColor,
    surface = CardColor,
    onSurface = TextColor,
    error = RedColor,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    // Force Dark Scheme for the premium P2P experience
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
