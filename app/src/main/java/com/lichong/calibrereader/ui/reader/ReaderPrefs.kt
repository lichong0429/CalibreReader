package com.lichong.calibrereader.ui.reader

import android.content.Context
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** 阅读外观偏好，全局共享（不区分书籍） */
data class ReaderPrefs(
    /** Readium 的 fontSize 是百分比，100 = 出版社原值 */
    val fontSize: Double = 100.0,
    val theme: Theme = Theme.LIGHT,
    val fontFamily: FontFamily? = null,
    val lineHeight: Double = 1.5,
    val scroll: Boolean = false
) {
    companion object {
        private const val FILE = "reader_prefs"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT = "font"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_SCROLL = "scroll"

        fun load(context: Context): ReaderPrefs {
            val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return ReaderPrefs(
                fontSize = sp.getFloat(KEY_FONT_SIZE, 100f).toDouble(),
                theme = runCatching { Theme.valueOf(sp.getString(KEY_THEME, null) ?: "") }
                    .getOrDefault(Theme.LIGHT),
                fontFamily = sp.getString(KEY_FONT, null)?.let { name ->
                    listOf(FontFamily.SERIF, FontFamily.SANS_SERIF)
                        .firstOrNull { it.name == name }
                },
                lineHeight = sp.getFloat(KEY_LINE_HEIGHT, 1.5f).toDouble(),
                scroll = sp.getBoolean(KEY_SCROLL, false)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_FONT_SIZE, fontSize.toFloat())
            .putString(KEY_THEME, theme.name)
            .putString(KEY_FONT, fontFamily?.name)
            .putFloat(KEY_LINE_HEIGHT, lineHeight.toFloat())
            .putBoolean(KEY_SCROLL, scroll)
            .apply()
    }
}

@OptIn(ExperimentalReadiumApi::class)
fun ReaderPrefs.toEpubPreferences(): EpubPreferences = EpubPreferences(
    fontSize = fontSize,
    theme = theme,
    fontFamily = fontFamily,
    lineHeight = lineHeight,
    scroll = scroll,
    publisherStyles = true
)
