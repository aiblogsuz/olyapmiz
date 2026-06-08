/**
 * Themes - Color palettes for the O'lyapmiz extension
 * Ported from Android's theme system
 */

const Themes = {
    // Theme names matching Android's ThemeOption enum
    LIGHT: 'LIGHT',
    DARK: 'DARK',
    AMOLED: 'AMOLED',
    CUSTOM: 'CUSTOM',

    /**
     * Get theme colors for a given theme.
     */
    getThemeColors(theme, customColors = {}) {
        switch (theme) {
            case this.LIGHT:
                return {
                    background: 0xFFF5F5F5,
                    filledDot: 0xFF2C2C2C,
                    emptyDot: 0xFFD0D0D0,
                    todayDot: 0xFF2196F3,
                    text: 0xFF1A1A1A,
                    textMuted: 0xFF757575,
                    accent: 0xFFFFC107,
                };

            case this.DARK:
                return {
                    background: 0xFF1A1A1A,
                    filledDot: 0xFFE0E0E0,
                    emptyDot: 0xFF3A3A3A,
                    todayDot: 0xFF5BA0E9,
                    text: 0xFFFFFFFF,
                    textMuted: 0xFFAAAAAA,
                    accent: 0xFFFFC62E,
                };

            case this.AMOLED:
                return {
                    background: 0xFF000000,
                    filledDot: 0xFFFFFFFF,
                    emptyDot: 0xFF2A2A2A,
                    todayDot: 0xFF5BA0E9,
                    text: 0xFFFFFFFF,
                    textMuted: 0xFF888888,
                    accent: 0xFFFFC62E,
                };

            case this.CUSTOM:
                return {
                    background: customColors.backgroundColor || 0xFF1A1A1A,
                    filledDot: customColors.filledDotColor || 0xFFE0E0E0,
                    emptyDot: customColors.emptyDotColor || 0xFF3A3A3A,
                    todayDot: customColors.todayDotColor || 0xFF5BA0E9,
                    text: 0xFFFFFFFF,
                    textMuted: 0xFFAAAAAA,
                    accent: 0xFFFFC62E,
                };

            default:
                // Default to AMOLED
                return this.getThemeColors(this.AMOLED);
        }
    },

    /**
     * Get CSS color string from integer color.
     */
    intToCSS(colorInt, alpha = 1.0) {
        const r = (colorInt >> 16) & 0xFF;
        const g = (colorInt >> 8) & 0xFF;
        const b = colorInt & 0xFF;
        if (alpha === 1.0) {
            return `rgb(${r},${g},${b})`;
        }
        return `rgba(${r},${g},${b},${alpha})`;
    },

    /**
     * Get all theme options for the popup UI.
     */
    getThemeOptions() {
        return [
            {
                id: this.LIGHT,
                label: 'Light',
                background: this.intToCSS(0xFFF5F5F5),
                text: this.intToCSS(0xFF2C2C2C),
                preview: [
                    this.intToCSS(0xFF69645D),
                    this.intToCSS(0xFF858078),
                ],
            },
            {
                id: this.DARK,
                label: 'Dark',
                background: this.intToCSS(0xFF25231F),
                text: this.intToCSS(0xFFE0E0E0),
                preview: [
                    this.intToCSS(0xFFB6B0A6),
                    this.intToCSS(0xFFE7E0D2),
                ],
            },
            {
                id: this.AMOLED,
                label: 'AMOLED',
                background: this.intToCSS(0xFF000000),
                text: this.intToCSS(0xFFFFFFFF),
                preview: [
                    this.intToCSS(0xFFE5E0D4),
                    this.intToCSS(0xFFFFC62E),
                ],
            },
            {
                id: this.CUSTOM,
                label: 'Custom',
                background: this.intToCSS(0xFF252015),
                text: this.intToCSS(0xFFE0E0E0),
                preview: [
                    this.intToCSS(0xFFD9C88B),
                    this.intToCSS(0xFFFFC62E),
                ],
            },
        ];
    },

    /**
     * Default settings for a new installation.
     */
    getDefaultSettings() {
        return {
            version: 1,
            theme: this.AMOLED,
            customColors: {
                backgroundColor: 0xFF1A1A1A,
                filledDotColor: 0xFFE0E0E0,
                emptyDotColor: 0xFF3A3A3A,
                todayDotColor: 0xFF5BA0E9,
            },
            visualMode: 'DOTS',  // 'DOTS' or 'X_MARKS'
            livedAlpha: 1.0,
            emptyAlpha: 0.6,
            position: {
                horizontalOffset: 0,
                verticalOffset: 7,
                scale: 1.0,
            },
            statsBandOffset: 0,
            totalWeeks: 4000,
            events: [],
        };
    },

    /**
     * Validate and merge settings with defaults.
     */
    validateSettings(settings) {
        const defaults = this.getDefaultSettings();
        const validated = { ...defaults };

        for (const key of Object.keys(defaults)) {
            if (settings[key] !== undefined) {
                validated[key] = settings[key];
            }
        }

        return validated;
    },
};

// Export
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { Themes };
}