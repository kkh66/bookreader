package com.bookreader.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Mostly black, white and gray palette (inspired by the Xiaohuanxiong Reader app): white background, light gray cards, black accents
 * surfaceVariant = card background, onSurfaceVariant = secondary text, outlineVariant = dividers
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF141414),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF141414),
    surface = Color.White,
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF8A8A8E),
    surfaceContainer = Color.White,
    outlineVariant = Color(0xFFE5E5E5),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color.White,
    secondaryContainer = Color(0xFFEAEAEA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF141414),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0E0E0F),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF98989D),
    surfaceContainer = Color(0xFF0E0E0F),
    outlineVariant = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFF2F2F2),
    inverseOnSurface = Color(0xFF141414),
    secondaryContainer = Color(0xFF2C2C2E),
)

@Composable
fun BookReaderTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
