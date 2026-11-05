package com.lichong.calibrereader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.lichong.calibrereader.data.prefs.AppPalette

/*
 * 设计 token 直接取自用户已有项目，保证跨软件视觉一致：
 *  - weknora-mobile/tailwind.config.js：brand / surface / ink / line / accent
 *  - AnyWords/frontend/src/index.css：--bg / --text / --accent / --border
 */

// weknora-mobile · brand
private val Violet50 = Color(0xFFEFEFFF)
private val Violet100 = Color(0xFFE4E2FF)
private val Violet300 = Color(0xFFA9A3FA)
private val Violet400 = Color(0xFF8B7CF2)
private val Violet500 = Color(0xFF6D5CF5)
private val Violet600 = Color(0xFF5B5BD6)
private val Violet700 = Color(0xFF4A48B8)
private val Violet900 = Color(0xFF2E2D6B)

// weknora-mobile · surface / ink / line
private val SurfaceSoft = Color(0xFFF6F7F9)
private val SurfaceSubtle = Color(0xFFF0F1F4)
private val Ink = Color(0xFF1A1D26)
private val InkSecondary = Color(0xFF5B5F66)
private val Line = Color(0xFFEDEDF2)

// weknora-mobile · accent
private val AccentBlue = Color(0xFF3B82F6)
private val AccentEmerald = Color(0xFF0FB87E)
private val AccentAmber = Color(0xFFF59E0B)

// AnyWords · 苹果风
private val AzurePrimary = Color(0xFF0071E3)
private val AzureDarkPrimary = Color(0xFF2997FF)
private val AppleBg = Color(0xFFF5F5F7)
private val AppleInk = Color(0xFF1D1D1F)
private val AppleSecondary = Color(0xFF6E6E73)
private val AppleBorder = Color(0xFFD2D2D7)

private val White = Color.White
private val ErrorLight = Color(0xFFE5484D)
private val ErrorDark = Color(0xFFFF8A8E)

fun lightSchemeFor(palette: AppPalette): ColorScheme = when (palette) {
    AppPalette.VIOLET -> lightColorScheme(
        primary = Violet600,
        onPrimary = White,
        primaryContainer = Violet100,
        onPrimaryContainer = Violet900,
        secondary = AccentBlue,
        onSecondary = White,
        secondaryContainer = Color(0xFFE8F1FF),
        onSecondaryContainer = Color(0xFF12345C),
        tertiary = AccentEmerald,
        onTertiary = White,
        tertiaryContainer = Color(0xFFDFF7EE),
        onTertiaryContainer = Color(0xFF0A4C34),
        background = SurfaceSoft,
        onBackground = Ink,
        surface = White,
        onSurface = Ink,
        surfaceVariant = SurfaceSubtle,
        onSurfaceVariant = InkSecondary,
        outline = Line,
        outlineVariant = Line,
        error = ErrorLight,
        onError = White,
        errorContainer = Color(0xFFFFE9E9),
        onErrorContainer = Color(0xFF7A1D20)
    )

    AppPalette.AZURE -> lightColorScheme(
        primary = AzurePrimary,
        onPrimary = White,
        primaryContainer = Color(0xFFE5F1FF),
        onPrimaryContainer = Color(0xFF003A75),
        secondary = AzureDarkPrimary,
        onSecondary = White,
        secondaryContainer = Color(0xFFDCEEFF),
        onSecondaryContainer = Color(0xFF00305E),
        tertiary = AccentAmber,
        onTertiary = White,
        tertiaryContainer = Color(0xFFFFF2D6),
        onTertiaryContainer = Color(0xFF5A3B00),
        background = AppleBg,
        onBackground = AppleInk,
        surface = White,
        onSurface = AppleInk,
        surfaceVariant = Color(0xFFECECF0),
        onSurfaceVariant = AppleSecondary,
        outline = AppleBorder,
        outlineVariant = Color(0xFFE5E5EA),
        error = Color(0xFFFF3B30),
        onError = White,
        errorContainer = Color(0xFFFFE9E7),
        onErrorContainer = Color(0xFF7A1B16)
    )
}

fun darkSchemeFor(palette: AppPalette): ColorScheme = when (palette) {
    AppPalette.VIOLET -> darkColorScheme(
        primary = Violet300,
        onPrimary = Violet900,
        primaryContainer = Violet700,
        onPrimaryContainer = Violet100,
        secondary = Violet400,
        onSecondary = Color(0xFF1B1740),
        secondaryContainer = Color(0xFF35316B),
        onSecondaryContainer = Violet100,
        tertiary = Color(0xFF5FD3A6),
        onTertiary = Color(0xFF053A27),
        tertiaryContainer = Color(0xFF134F3A),
        onTertiaryContainer = Color(0xFFCFF5E6),
        background = Color(0xFF121317),
        onBackground = Color(0xFFECEEF3),
        surface = Color(0xFF1C1E24),
        onSurface = Color(0xFFECEEF3),
        surfaceVariant = Color(0xFF26282F),
        onSurfaceVariant = Color(0xFFB4B9C3),
        outline = Color(0xFF2E323A),
        outlineVariant = Color(0xFF262A31),
        error = ErrorDark,
        onError = Color(0xFF4A1416),
        errorContainer = Color(0xFF5C2528),
        onErrorContainer = Color(0xFFFFD9DA)
    )

    AppPalette.AZURE -> darkColorScheme(
        primary = AzureDarkPrimary,
        onPrimary = Color(0xFF00214B),
        primaryContainer = Color(0xFF00407F),
        onPrimaryContainer = Color(0xFFD6EAFF),
        secondary = Color(0xFF7FC4FF),
        onSecondary = Color(0xFF002A55),
        secondaryContainer = Color(0xFF0B4577),
        onSecondaryContainer = Color(0xFFD6EAFF),
        tertiary = Color(0xFFFFD166),
        onTertiary = Color(0xFF4A3300),
        tertiaryContainer = Color(0xFF5A3F00),
        onTertiaryContainer = Color(0xFFFFEFC2),
        background = Color(0xFF1D1D1F),
        onBackground = Color(0xFFF5F5F7),
        surface = Color(0xFF2D2D2F),
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF3A3A3D),
        onSurfaceVariant = Color(0xFFA1A1A6),
        outline = Color(0xFF424245),
        outlineVariant = Color(0xFF353537),
        error = Color(0xFFFF453A),
        onError = Color(0xFF4A0F0B),
        errorContainer = Color(0xFF5C201B),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

/** 少量品牌常量，供按钮/插画等不直接吃 ColorScheme 的地方复用。 */
object BrandColors {
    val Violet = Violet500
    val VioletLight = Violet300
    val VioletDeep = Violet700
    val Azure = AzurePrimary
    val AzureLight = AzureDarkPrimary
    val Amber = AccentAmber
    val Emerald = AccentEmerald
    val SoftViolet = Violet50
}
