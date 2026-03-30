package com.example.papertranslator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryWarm,
    secondary = SecondaryWarm,
    tertiary = PrimaryVariantWarm
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryWarm,
    onPrimary = OnPrimaryWarm,
    primaryContainer = PrimaryVariantWarm,
    secondary = SecondaryWarm,
    onSecondary = OnSecondaryWarm,
    background = BackgroundWarm,
    surface = SurfaceWarm,
    error = ErrorColor
)

@Composable
fun PaperTranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
