package com.lichong.calibrereader.ui.reader

import android.content.Context
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** 阅读外观偏好，全局共享（不区分书籍） */
data class ReaderPrefs(
    /** Readium 的 fontSize 是百分比，100 = 出版社原值 */
    val fontSize: Double = 100.0,
    val theme: Theme = Theme.LIGHT,
    val fontFamily: FontFamily? = null,
    val lineHeight: Double = 1.5,
    val scroll: Boolean = false,
    /**
     * 是否保留出版社 CSS。
     *
     * 默认 false：之前是 true，出版社会把 fontSize / lineHeight 全部盖掉，
     * 表现就是「字号和行距怎么调都没变化」。
     */
    val publisherStyles: Boolean = false,
    val pageMargins: Double = 0.5,
    val paragraphSpacing: Double = 0.0,
    val paragraphIndent: Double = 0.0,
    val textAlign: TextAlign? = null,
    /** 100 = 常规，300 细，700 粗 */
    val fontWeight: Double? = null,
    val letterSpacing: Double = 0.0,
    val hyphens: Boolean = false
) {
    companion object {
        private const val FILE = "reader_prefs"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT = "font"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_SCROLL = "scroll"
        private const val KEY_PUBLISHER_STYLES = "publisher_styles"
        private const val KEY_MARGINS = "page_margins"
        private const val KEY_PARA_SPACING = "para_spacing"
        private const val KEY_PARA_INDENT = "para_indent"
        private const val KEY_ALIGN = "text_align"
        private const val KEY_WEIGHT = "font_weight"
        private const val KEY_LETTER = "letter_spacing"
        private const val KEY_HYPHENS = "hyphens"

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
                scroll = sp.getBoolean(KEY_SCROLL, false),
                publisherStyles = sp.getBoolean(KEY_PUBLISHER_STYLES, false),
                pageMargins = sp.getFloat(KEY_MARGINS, 0.5f).toDouble(),
                paragraphSpacing = sp.getFloat(KEY_PARA_SPACING, 0f).toDouble(),
                paragraphIndent = sp.getFloat(KEY_PARA_INDENT, 0f).toDouble(),
                textAlign = sp.getString(KEY_ALIGN, null)?.let { name ->
                    runCatching { TextAlign.valueOf(name) }.getOrNull()
                },
                fontWeight = if (sp.contains(KEY_WEIGHT)) {
                    sp.getFloat(KEY_WEIGHT, 100f).toDouble()
                } else null,
                letterSpacing = sp.getFloat(KEY_LETTER, 0f).toDouble(),
                hyphens = sp.getBoolean(KEY_HYPHENS, false)
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
            .putBoolean(KEY_PUBLISHER_STYLES, publisherStyles)
            .putFloat(KEY_MARGINS, pageMargins.toFloat())
            .putFloat(KEY_PARA_SPACING, paragraphSpacing.toFloat())
            .putFloat(KEY_PARA_INDENT, paragraphIndent.toFloat())
            .putString(KEY_ALIGN, textAlign?.name)
            .apply {
                if (fontWeight != null) putFloat(KEY_WEIGHT, fontWeight.toFloat())
                else remove(KEY_WEIGHT)
            }
            .putFloat(KEY_LETTER, letterSpacing.toFloat())
            .putBoolean(KEY_HYPHENS, hyphens)
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
    // 关键：publisherStyles = true 会让出版商的 CSS 覆盖字号/行距，
    // 用户就感觉「调了没反应」。默认 false，需要的书可手动打开。
    publisherStyles = publisherStyles,
    pageMargins = pageMargins,
    paragraphSpacing = paragraphSpacing,
    paragraphIndent = paragraphIndent,
    textAlign = textAlign,
    fontWeight = fontWeight,
    letterSpacing = letterSpacing.takeIf { it > 0.0 },
    hyphens = hyphens
)
