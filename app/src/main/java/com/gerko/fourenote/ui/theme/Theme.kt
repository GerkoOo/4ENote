package com.gerko.fourenote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Forest40,
    secondary = Forest80,
    tertiary = Sand40,
    background = Sand90,
    error = Error40,
)

private val DarkColors = darkColorScheme(
    primary = Forest80,
    secondary = Forest40,
    tertiary = Sand40,
    error = Error40,
)

@Composable
fun FourENoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
