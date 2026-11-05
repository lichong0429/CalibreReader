package com.lichong.calibrereader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.lichong.calibrereader.data.prefs.AppPalette
import com.lichong.calibrereader.data.prefs.ThemeMode

/**
 * 统一主题入口。
 *
 * 刻意**不启用 dynamicColor**：Material You 会按壁纸生成配色，把自定义品牌色完全冲掉，
 * 同一台机器上每次观感都不一样，也是之前界面「杂、不受控」的主要原因。
 * 颜色改由 AppPalette 显式决定，并在设置页可切换。
 */
@Composable
fun CalibreTheme(
    palette: AppPalette = AppPalette.VIOLET,
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) darkSchemeFor(palette) else lightSchemeFor(palette),
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
