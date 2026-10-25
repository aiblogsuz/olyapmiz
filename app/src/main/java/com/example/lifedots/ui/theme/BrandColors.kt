package com.example.lifedots.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette for the app chrome (MainActivity + SettingsActivity).
 *
 * NOTE: this is NOT for the wallpaper renderer — the wallpaper still
 * reads its own `ThemeColors` derived from `ThemeOption`/`CustomColors`
 * in `LifeDotsPreferences`. These tokens are only for Home + Settings.
 */
object BrandColors {
    val AmberGold = Color(0xFFFFB300)           // primary brand
    val AmberGoldDark = Color(0xFFE89E00)       // pressed / hover state
    val AmberGoldWash = Color(0x33FFB300)       // 20% alpha tint

    val InkBlack = Color(0xFF0A0A0A)            // primary surface / button fill
    val InkBlackElevated = Color(0xFF171717)    // raised cards
    val InkBlackDeep = Color(0xFF000000)        // shadows / true black

    val DarkAmber = Color(0xFF3D2900)           // muted text on yellow
    val GoldenMuted = Color(0x99FFB300)         // muted text on black (60% alpha)
    val OffWhite = Color(0xFFF5F5F5)            // body text on dark
    val HairlineGold = Color(0x40FFB300)        // card borders / dividers (25% alpha)
}
