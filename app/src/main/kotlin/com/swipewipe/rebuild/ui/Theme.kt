package com.swipewipe.rebuild.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF42A5F5)
val DangerRed = Color(0xFFEF5350)
val SurfaceBg = Color(0xFF101418)

private val DarkScheme = darkColorScheme(
    primary = BrandBlue,
    secondary = Color(0xFF7E57C2),
    tertiary = Color(0xFF26C6DA),
    background = Color(0xFF101418),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF1F2733),
    error = DangerRed
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF5E35B1),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEFF1),
    error = Color(0xFFD32F2F)
)

@Composable
fun SwipewipeTheme(darkMode: Int, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}
