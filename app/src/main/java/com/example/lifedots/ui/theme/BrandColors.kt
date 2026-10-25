package com.example.lifedots.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand-fixed palette for the app chrome (MainActivity + SettingsActivity).
 *
 * The wallpaper renderer does NOT read these — it derives its own
 * ThemeColors from the user's ThemeOption (see
 * LifeDotsWallpaperService.getThemeColors()).
 *
 * Value provenance: amber-gold accent #FFB300 and ink #0A0A0A come from
 * the brand spec noted in Theme.kt; the elevated surface, off-white and
 * muted/hairline golds match the hex values already in use across the
 * settings UI (ModernPanel, ModernGoldMuted, UmrDobRow text).
 */
object BrandColors {
    /** Near-pure black app background. */
    val InkBlack = Color(0xFF0A0A0A)

    /** Raised card/sheet surface on top of InkBlack. */
    val InkBlackElevated = Color(0xFF141414)

    /** Primary readable text on ink surfaces. */
    val OffWhite = Color(0xFFEDE8DE)

    /** Warm amber-gold accent. */
    val AmberGold = Color(0xFFFFB300)

    /** Tinted container behind gold accents (primaryContainer / disabled fills). */
    val AmberGoldWash = Color(0xFF332A15)

    /** Desaturated gold for secondary text. */
    val GoldenMuted = Color(0xFFC7A35F)

    /** Faint gold used for outlines and dividers. */
    val HairlineGold = Color(0xFF3B331F)
}
