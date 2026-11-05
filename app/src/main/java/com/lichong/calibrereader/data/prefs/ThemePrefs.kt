package com.lichong.calibrereader.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 两套配色分别对齐用户已有软件：weknora-mobile（墨紫）与 AnyWords（晴蓝）。 */
enum class AppPalette(val label: String) {
    VIOLET("墨紫 · WeKnora"),
    AZURE("晴蓝 · AnyWords")
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

data class ThemeSettings(
    val palette: AppPalette = AppPalette.VIOLET,
    val mode: ThemeMode = ThemeMode.SYSTEM
)

/** 外观偏好：轻量 SharedPreferences + StateFlow，切换后 MainActivity 立即重组主题。 */
class ThemePrefs(context: Context) {

    private val sp = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    private fun read(): ThemeSettings = ThemeSettings(
        palette = enumValue(sp.getString(KEY_PALETTE, null), AppPalette.VIOLET),
        mode = enumValue(sp.getString(KEY_MODE, null), ThemeMode.SYSTEM)
    )

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        runCatching { enumValues<T>().first { it.name == raw } }.getOrDefault(fallback)

    fun setPalette(palette: AppPalette) {
        sp.edit().putString(KEY_PALETTE, palette.name).apply()
        _settings.value = read()
    }

    fun setMode(mode: ThemeMode) {
        sp.edit().putString(KEY_MODE, mode.name).apply()
        _settings.value = read()
    }

    private companion object {
        const val KEY_PALETTE = "palette"
        const val KEY_MODE = "mode"
    }
}
