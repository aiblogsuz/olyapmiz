package com.example.lifedots.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App-chrome color scheme. Brand-fixed: warm amber-gold accent
 * (#FFB300) on near-pure black (#0A0A0A) surfaces.
 *
 * NOTE: This drives only the app chrome (MainActivity +
 * SettingsActivity). The wallpaper renderer reads its own
 * ThemeColors derived from the user's ThemeOption — see
 * LifeDotsWallpaperService.getThemeColors().
 */
private val BrandDarkColorScheme = darkColorScheme(
    primary = BrandColors.AmberGold,
    onPrimary = BrandColors.InkBlack,
    primaryContainer = BrandColors.AmberGoldWash,
    onPrimaryContainer = BrandColors.AmberGold,

    secondary = BrandColors.AmberGold,
    onSecondary = BrandColors.InkBlack,
    secondaryContainer = BrandColors.AmberGoldWash,
    onSecondaryContainer = BrandColors.AmberGold,

    tertiary = BrandColors.AmberGold,
    onTertiary = BrandColors.InkBlack,

    background = BrandColors.InkBlack,
    onBackground = BrandColors.OffWhite,

    surface = BrandColors.InkBlackElevated,
    onSurface = BrandColors.OffWhite,
    surfaceVariant = BrandColors.InkBlackElevated,
    onSurfaceVariant = BrandColors.GoldenMuted,

    outline = BrandColors.HairlineGold,
    outlineVariant = BrandColors.HairlineGold,

    error = Color(0xFFE53935),
    onError = BrandColors.OffWhite,
)

@Composable
fun LifeDotsTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BrandDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
