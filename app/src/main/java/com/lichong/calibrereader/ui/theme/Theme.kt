package com.lichong.calibrereader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Ink = Color(0xFF16262E)
private val Teal = Color(0xFF1B4B5A)
private val TealLight = Color(0xFF3E7C8C)
private val Paper = Color(0xFFF7F3EA)
private val Sand = Color(0xFFE4D9C6)
private val Clay = Color(0xFF9A5B3D)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE6EC),
    onPrimaryContainer = Ink,
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DCCF),
    onSecondaryContainer = Color(0xFF3B1E10),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFDFBF6),
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Color(0xFF4A4238),
    outline = Color(0xFF9C9384)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FD3E0),
    onPrimary = Color(0xFF04222B),
    primaryContainer = Color(0xFF1B4B5A),
    onPrimaryContainer = Color(0xFFCFE6EC),
    secondary = Color(0xFFE0B08F),
    onSecondary = Color(0xFF3B1E10),
    secondaryContainer = Color(0xFF57301C),
    onSecondaryContainer = Color(0xFFF3DCCF),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFE6E1D7),
    surface = Color(0xFF161C21),
    onSurface = Color(0xFFE6E1D7),
    surfaceVariant = Color(0xFF2A3138),
    onSurfaceVariant = Color(0xFFC4BCAF),
    outline = Color(0xFF6E767D)
)

@Composable
fun CalibreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }

        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
