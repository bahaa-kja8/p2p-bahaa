package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BgColor = Color(0xFF0D1117)
val CardColor = Color(0xFF161B22)
val CardColorLight = Color(0xFF1F242C)
val BorderColor = Color(0xFF30363D)

val GoldColor = Color(0xFFF0B90B)
val GreenColor = Color(0xFF3FB950)
val RedColor = Color(0xFFF85149)
val TextColor = Color(0xFFE6EDF3)
val TextSecondaryColor = Color(0xFF7D8590)
val TextTertiaryColor = Color(0xFF8B949E)

// Modern design system gradients
val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0D1117), Color(0xFF05070A))
)

val GoldGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFF0B90B), Color(0xFFFFD700))
)

val GreenGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF2EA043), Color(0xFF3FB950))
)

val RedGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFDA3633), Color(0xFFF85149))
)

val BlueGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF1F6FEB), Color(0xFF58A6FF))
)

val PurpleGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF8957E5), Color(0xFFBC8CFF))
)

val HeroGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFF0B90B), Color(0xFF8957E5))
)
