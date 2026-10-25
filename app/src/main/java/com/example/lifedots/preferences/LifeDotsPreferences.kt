package com.example.lifedots.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.UUID

enum class ThemeOption {
    LIGHT, DARK, AMOLED, CUSTOM
}

enum class DotSize {
    TINY, SMALL, MEDIUM, LARGE, HUGE
}

enum class DotShape {
    CIRCLE, SQUARE, ROUNDED_SQUARE, DIAMOND
}

enum class GridDensity {
    COMPACT, NORMAL, RELAXED, SPACIOUS
}

// Feature 3: Dot Effects
enum class DotStyle {
    FLAT, GRADIENT, OUTLINED, SOFT_GLOW, NEON, EMBOSSED
}

data class DotEffectSettings(
    val style: DotStyle = DotStyle.FLAT,
    val glowRadius: Float = 8f,
    val outlineWidth: Float = 2f
)

// Feature 2: Footer Text
enum class TextAlignment {
    LEFT, CENTER, RIGHT
}

data class FooterTextSettings(
    val enabled: Boolean = false,
    val text: String = "",
    val fontSize: Float = 14f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val alignment: TextAlignment = TextAlignment.CENTER
)

// Features 4 & 5: View Modes
enum class ViewMode {
    CONTINUOUS, MONTHLY, CALENDAR
}

data class ViewModeSettings(
    val mode: ViewMode = ViewMode.CALENDAR,
    val showMonthLabels: Boolean = true,
    val monthLabelColor: Int = 0xFFFFFFFF.toInt()
)

data class CalendarViewSettings(
    val columnsPerRow: Int = 3,  // 3x4 or 4x3 grid
    val showYearStats: Boolean = true,
    val mondayFirst: Boolean = true,
    // Highlight the current calendar week with a warm tint and glow on today
    val highlightCurrentWeek: Boolean = true,
    val currentWeekColor: Int = 0xFFFFD54F.toInt(),  // warm yellow
    // Optional milestone event (week tinted; countdown shown below year stats).
    // Disabled by default so a fresh install shows no personal date.
    val eventEnabled: Boolean = false,
    val eventMonth: Int = 0,         // 0-indexed; 0 = January
    val eventDay: Int = 1,
    val eventLabel: String = "",
    val eventColor: Int = 0xFFEF5350.toInt()  // warm red
)

// Feature 6: Goal Tracking
enum class GoalPosition {
    TOP, BOTTOM
}

data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetDate: Long,
    // Vivid Material Red 600 — the color the user "loved" from the wedding event.
    // Shows as a glowing red dot on the calendar + tints the countdown line below.
    val color: Int = 0xFFE53935.toInt()
)

data class GoalSettings(
    // Goals are rendered as colored dots in the calendar + a countdown line
    // below the year stats (the same look the wedding event used to have).
    // Enabled by default so users see the slot waiting to be filled.
    val enabled: Boolean = true,
    val goals: List<Goal> = emptyList(),
    val position: GoalPosition = GoalPosition.BOTTOM
)

// Umr-only feature. Separate from Goal: events can be past OR future, and
// future ones appear as a "weeks remaining" line under the Umr grid. Same
// data shape as Goal but kept as a distinct type so the two never bleed
// into each other.
data class Event(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetDate: Long,
    val color: Int = 0xFFE53935.toInt()
)

data class EventSettings(
    val enabled: Boolean = true,
    val events: List<Event> = emptyList(),
)

data class CustomColors(
    val backgroundColor: Int = 0xFF1A1A1A.toInt(),
    val filledDotColor: Int = 0xFFE0E0E0.toInt(),
    val emptyDotColor: Int = 0xFF3A3A3A.toInt(),
    val todayDotColor: Int = 0xFF5BA0E9.toInt()
)

// ===== NEW ADVANCED FEATURES =====

// Custom Positioning & Scaling
data class PositionSettings(
    val horizontalOffset: Float = 0f,  // -50 to 50 percent
    val verticalOffset: Float = 0f,    // -50 to 50
    val scale: Float = 1.0f            // 0.5 to 1.5
)

// Animation Types
enum class AnimationType {
    NONE,
    FADE_IN,
    PULSE,
    WAVE,
    BREATHE,
    RIPPLE,
    CASCADE
}

data class AnimationSettings(
    val enabled: Boolean = false,
    val type: AnimationType = AnimationType.NONE,
    val speed: Float = 1.0f,           // 0.5 to 2.0
    val intensity: Float = 0.5f        // 0.1 to 1.0
)

// Glass/Frosted Effect
enum class GlassStyle {
    NONE,
    LIGHT_FROST,
    HEAVY_FROST,
    ACRYLIC,
    CRYSTAL,
    ICE
}

data class GlassEffectSettings(
    val enabled: Boolean = false,
    val style: GlassStyle = GlassStyle.NONE,
    val blur: Float = 10f,             // 0 to 25
    val opacity: Float = 0.3f,         // 0.1 to 0.9
    val tint: Int = 0x80FFFFFF.toInt() // Tint color with alpha
)

// Tree Growth Effect
enum class TreeStyle {
    SIMPLE,
    DETAILED,
    BONSAI,
    SAKURA,
    WILLOW
}

data class TreeEffectSettings(
    val enabled: Boolean = false,
    val style: TreeStyle = TreeStyle.SIMPLE,
    val trunkColor: Int = 0xFF8B4513.toInt(),
    val leafColor: Int = 0xFF228B22.toInt(),
    val bloomColor: Int = 0xFFFF69B4.toInt(),
    val showGround: Boolean = true
)

// Fluid/Liquid Effects
enum class FluidStyle {
    NONE,
    WATER,
    LAVA,
    MERCURY,
    PLASMA,
    AURORA
}

data class FluidEffectSettings(
    val enabled: Boolean = false,
    val style: FluidStyle = FluidStyle.NONE,
    val flowSpeed: Float = 1.0f,
    val turbulence: Float = 0.5f,
    val colorIntensity: Float = 0.7f
)

// Special Visual Mode combining multiple effects
enum class VisualTheme {
    CLASSIC,           // Default dot grid
    MINIMALIST,        // Clean, simple
    CYBERPUNK,         // Neon, glowing
    NATURE,            // Tree growth
    FLUID,             // Liquid effects
    GLASS,             // Frosted glass
    COSMIC             // Space-themed
}

enum class TopViewMode { YIL, UMR }

enum class UmrVisualMode { DOTS, X_MARKS }

// How the user wants "Set as Wallpaper" to behave.
//   BOTH_LIVE: the standard path — the live wallpaper engine paints Yil/Umr
//     on both home and lock screen (Android's default for a live wallpaper).
//   CALENDAR_LOCK_BLACK_HOME: paint today's Yil/Umr snapshot onto the lock
//     screen and pure black onto the home screen. Refreshed at midnight + on
//     app open so the lock screen calendar always shows the current day.
enum class WallpaperApplyMode { BOTH_LIVE, CALENDAR_LOCK_BLACK_HOME }

data class AutoSwitchSettings(
    val enabled: Boolean = false,
    val intervalMs: Long = 5_000L,
    val referenceMs: Long = 0L,
    val startMode: TopViewMode = TopViewMode.YIL,
)

data class UmrSettings(
    val birthdayEpochMs: Long = 0L,
    val momBirthdayEpochMs: Long = 0L,
    val dadBirthdayEpochMs: Long = 0L,
    val visualMode: UmrVisualMode = UmrVisualMode.DOTS,
    val livedAlpha: Float = 1.0f,
    val emptyAlpha: Float = 0.6f,
    val totalWeeks: Int = 4000,
    // Umr-only position. Yil uses WallpaperSettings.positionSettings.
    // Default vertical offset 7% — the 80x52 grid sits a touch lower on the
    // canvas than Yil because the top counter band needs breathing room.
    val position: PositionSettings = PositionSettings(verticalOffset = 7f),
    // Independent vertical shift for just the stats counter band, in percent
    // of canvas height. Used on phones where the band collides with the
    // system clock / swipe-to-unlock text (e.g. Redmi 8). Negative = up.
    val statsBandOffset: Float = 0f,
)

data class WallpaperSettings(
    val theme: ThemeOption = ThemeOption.AMOLED,
    val dotSize: DotSize = DotSize.MEDIUM,
    val dotShape: DotShape = DotShape.CIRCLE,
    val gridDensity: GridDensity = GridDensity.COMPACT,
    val highlightToday: Boolean = true,
    val filledDotAlpha: Float = 1.0f,
    // Yil-only Y shift for the bottom stats block (day/year counters +
    // optional goal countdown lines). Parallels umrSettings.statsBandOffset.
    val yilStatsBandOffset: Float = 0f,
    val emptyDotAlpha: Float = 1.0f,
    val customColors: CustomColors = CustomColors(),
    // Feature settings
    val dotEffectSettings: DotEffectSettings = DotEffectSettings(),
    val footerTextSettings: FooterTextSettings = FooterTextSettings(),
    val viewModeSettings: ViewModeSettings = ViewModeSettings(),
    val calendarViewSettings: CalendarViewSettings = CalendarViewSettings(),
    val goalSettings: GoalSettings = GoalSettings(),
    val eventSettings: EventSettings = EventSettings(),
    // Advanced feature settings
    val positionSettings: PositionSettings = PositionSettings(),
    val animationSettings: AnimationSettings = AnimationSettings(),
    val glassEffectSettings: GlassEffectSettings = GlassEffectSettings(),
    val treeEffectSettings: TreeEffectSettings = TreeEffectSettings(),
    val fluidEffectSettings: FluidEffectSettings = FluidEffectSettings(),
    val visualTheme: VisualTheme = VisualTheme.CLASSIC,
    val topViewMode: TopViewMode = TopViewMode.YIL,
    val autoSwitchSettings: AutoSwitchSettings = AutoSwitchSettings(),
    val umrSettings: UmrSettings = UmrSettings(),
    val soundsEnabled: Boolean = true,
    val vibrationsEnabled: Boolean = true,
    // Branch for the "Set as Wallpaper" tap. See WallpaperApplyMode docs.
    val applyMode: WallpaperApplyMode = WallpaperApplyMode.BOTH_LIVE,
)

/**
 * Resolve which view mode is currently effective.
 *
 * When auto-switch is off, returns the user-picked `topViewMode`.
 * When auto-switch is on, returns the mode based on a wall-clock
 * formula: `mode = floor((now - referenceMs) / intervalMs) % 2`,
 * starting from `startMode`. This is a pure function and is called
 * by the wallpaper engine on every draw. No state, no drift.
 *
 * Safety: if auto-switch is enabled but birthday is unset, returns
 * YIL — Umr's "weeks lived" can't be computed without it.
 */
fun currentEffectiveMode(now: Long, settings: WallpaperSettings): TopViewMode {
    val auto = settings.autoSwitchSettings
    if (!auto.enabled) return settings.topViewMode
    if (settings.umrSettings.birthdayEpochMs == 0L) return TopViewMode.YIL
    val elapsed = now - auto.referenceMs
    val ticks = if (auto.intervalMs > 0L) elapsed / auto.intervalMs else 0L
    val normalizedTicks = if (ticks < 0L) 0L else ticks   // clock-skew safety
    val startIsYil = auto.startMode == TopViewMode.YIL
    val onStartSide = (normalizedTicks % 2L) == 0L
    return when {
        onStartSide && startIsYil -> TopViewMode.YIL
        onStartSide && !startIsYil -> TopViewMode.UMR
        !onStartSide && startIsYil -> TopViewMode.UMR
        else -> TopViewMode.YIL
    }
}

/** An explicit pick begins a new rotation interval immediately. */
fun selectCalendar(settings: WallpaperSettings, mode: TopViewMode, now: Long): WallpaperSettings =
    settings.copy(topViewMode = mode, autoSwitchSettings = settings.autoSwitchSettings.copy(
        referenceMs = now, startMode = mode,
    ))

class LifeDotsPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        runMigrationsIfNeeded()
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<WallpaperSettings> = _settingsFlow.asStateFlow()

    val settings: WallpaperSettings
        get() = _settingsFlow.value

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T {
        val raw = prefs.getString(key, default.name) ?: default.name
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    /**
     * One-time rebrand migrations for users upgrading from the upstream LifeDots
     * fork (or any earlier build before the O'lyapmiz rebrand). These migrations
     * preserve user settings while cleaning up keys that no longer exist.
     */
    private fun runMigrationsIfNeeded() {
        val stored = prefs.getInt(KEY_MIGRATION_VERSION, 0)
        if (stored >= CURRENT_MIGRATION_VERSION) return
        val editor = prefs.edit()
        // v1 used to force-clear KEY_VIEW_MODE. That made some upgrades lose
        // intentional Monthly/Continuous selections. Keep saved values now;
        // fresh installs with no key already use CALENDAR below.
        if (stored < 2) {
            // v2: the Calendar's single milestone event has been replaced by
            // the general Goal countdown. If the user had an event configured,
            // convert it to a Goal so they don't lose their wedding/birthday/etc.
            val hadEvent = prefs.getBoolean(KEY_CALENDAR_EVENT_ENABLED, false)
            val label = prefs.getString(KEY_CALENDAR_EVENT_LABEL, "")?.takeIf { it.isNotBlank() }
            val month = prefs.getInt(KEY_CALENDAR_EVENT_MONTH, -1)
            val day = prefs.getInt(KEY_CALENDAR_EVENT_DAY, -1)
            val color = prefs.getInt(KEY_CALENDAR_EVENT_COLOR, 0xFFE53935.toInt())
            if (hadEvent && label != null && month in 0..11 && day in 1..31) {
                val existingJson = prefs.getString(KEY_GOALS_JSON, "[]") ?: "[]"
                val type = object : TypeToken<List<Goal>>() {}.type
                val existing: List<Goal> = try { gson.fromJson(existingJson, type) ?: emptyList() } catch (e: Exception) { emptyList() }
                val calendar = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                val newGoal = Goal(title = label, targetDate = calendar.timeInMillis, color = color)
                editor.putString(KEY_GOALS_JSON, gson.toJson(existing + newGoal))
                editor.putBoolean(KEY_GOALS_ENABLED, true)
            }
            // Wipe legacy event keys regardless — even if migration didn't add a
            // goal, the renderer no longer reads these so they're dead weight.
            editor.remove(KEY_CALENDAR_EVENT_ENABLED)
            editor.remove(KEY_CALENDAR_EVENT_LABEL)
            editor.remove(KEY_CALENDAR_EVENT_MONTH)
            editor.remove(KEY_CALENDAR_EVENT_DAY)
            editor.remove(KEY_CALENDAR_EVENT_COLOR)
        }
        // Missing keys use defaults; upgrades never overwrite a saved position.
        if (stored < 9) {
            // v9: introduces Umr life view + Yil/Umr auto-switch. No destructive
            // writes — fresh installs and upgraders both pick up the data-class
            // defaults (Yil selected, auto-switch off, birthday unset). The
            // migration just marks the schema seen so future migrations can
            // assume any v9-or-later state.
        }
        if (stored < 10) {
            // v10: introduces soundsEnabled + vibrationsEnabled. Both default
            // true via the data-class defaults — no destructive writes needed.
        }
        if (stored < 11) {
            // v11: introduce parent birthdays + Umr visual mode + Umr-only alphas.
            // Pure additive — only writes defaults for missing keys, never touches
            // existing user data. Safe to re-run if interrupted.
            val edit = prefs.edit()
            if (!prefs.contains(KEY_UMR_MOM_BIRTHDAY_MS)) edit.putLong(KEY_UMR_MOM_BIRTHDAY_MS, 0L)
            if (!prefs.contains(KEY_UMR_DAD_BIRTHDAY_MS)) edit.putLong(KEY_UMR_DAD_BIRTHDAY_MS, 0L)
            if (!prefs.contains(KEY_UMR_VISUAL_MODE)) edit.putString(KEY_UMR_VISUAL_MODE, UmrVisualMode.DOTS.name)
            if (!prefs.contains(KEY_UMR_LIVED_ALPHA)) edit.putFloat(KEY_UMR_LIVED_ALPHA, 1.0f)
            if (!prefs.contains(KEY_UMR_EMPTY_ALPHA)) edit.putFloat(KEY_UMR_EMPTY_ALPHA, 0.6f)
            if (!prefs.contains(KEY_UMR_TOTAL_WEEKS)) edit.putInt(KEY_UMR_TOTAL_WEEKS, 4000)
            edit.putInt(KEY_MIGRATION_VERSION, 11).apply()
        }
        // Week start uses a default only when absent, preserving explicit choices.
        editor.putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION).apply()
    }

    private fun loadSettings(): WallpaperSettings {
        val customColors = CustomColors(
            backgroundColor = prefs.getInt(KEY_CUSTOM_BG_COLOR, 0xFF1A1A1A.toInt()),
            filledDotColor = prefs.getInt(KEY_CUSTOM_FILLED_COLOR, 0xFFE0E0E0.toInt()),
            emptyDotColor = prefs.getInt(KEY_CUSTOM_EMPTY_COLOR, 0xFF3A3A3A.toInt()),
            todayDotColor = prefs.getInt(KEY_CUSTOM_TODAY_COLOR, 0xFF5BA0E9.toInt())
        )

        // Feature 3: Dot Effects
        val dotEffectSettings = DotEffectSettings(
            style = enumPref(KEY_DOT_STYLE, DotStyle.FLAT),
            glowRadius = prefs.getFloat(KEY_GLOW_RADIUS, 8f),
            outlineWidth = prefs.getFloat(KEY_OUTLINE_WIDTH, 2f)
        )

        // Feature 2: Footer Text
        val footerTextSettings = FooterTextSettings(
            enabled = prefs.getBoolean(KEY_FOOTER_ENABLED, false),
            text = prefs.getString(KEY_FOOTER_TEXT, "") ?: "",
            fontSize = prefs.getFloat(KEY_FOOTER_FONT_SIZE, 14f),
            color = prefs.getInt(KEY_FOOTER_COLOR, 0xFFFFFFFF.toInt()),
            alignment = enumPref(KEY_FOOTER_ALIGNMENT, TextAlignment.CENTER)
        )

        // Features 4 & 5: View Modes
        val viewModeSettings = ViewModeSettings(
            mode = enumPref(KEY_VIEW_MODE, ViewMode.CALENDAR),
            showMonthLabels = prefs.getBoolean(KEY_SHOW_MONTH_LABELS, true),
            monthLabelColor = prefs.getInt(KEY_MONTH_LABEL_COLOR, 0xFFFFFFFF.toInt())
        )

        val calendarViewSettings = CalendarViewSettings(
            columnsPerRow = prefs.getInt(KEY_CALENDAR_COLUMNS, 3),
            showYearStats = prefs.getBoolean(KEY_CALENDAR_STATS, true),
            mondayFirst = prefs.getBoolean(KEY_CALENDAR_MONDAY_FIRST, true),
            highlightCurrentWeek = prefs.getBoolean(KEY_CALENDAR_HIGHLIGHT_WEEK, true),
            currentWeekColor = prefs.getInt(KEY_CALENDAR_WEEK_COLOR, 0xFFFFD54F.toInt()),
            eventEnabled = prefs.getBoolean(KEY_CALENDAR_EVENT_ENABLED, false),
            eventMonth = prefs.getInt(KEY_CALENDAR_EVENT_MONTH, 0),
            eventDay = prefs.getInt(KEY_CALENDAR_EVENT_DAY, 1),
            eventLabel = prefs.getString(KEY_CALENDAR_EVENT_LABEL, "") ?: "",
            eventColor = prefs.getInt(KEY_CALENDAR_EVENT_COLOR, 0xFFEF5350.toInt())
        )

        // Feature 6: Goal Tracking
        val goalsJson = prefs.getString(KEY_GOALS_JSON, "[]") ?: "[]"
        val goalsType = object : TypeToken<List<Goal>>() {}.type
        val goals: List<Goal> = try {
            gson.fromJson(goalsJson, goalsType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val goalSettings = GoalSettings(
            enabled = prefs.getBoolean(KEY_GOALS_ENABLED, true),
            goals = goals,
            position = enumPref(KEY_GOALS_POSITION, GoalPosition.BOTTOM)
        )

        // Umr Events (separate from Goal — events can be past or future)
        val eventsJson = prefs.getString(KEY_EVENTS_JSON, "[]") ?: "[]"
        val eventsType = object : TypeToken<List<Event>>() {}.type
        val events: List<Event> = try {
            gson.fromJson(eventsJson, eventsType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val eventSettings = EventSettings(
            enabled = prefs.getBoolean(KEY_EVENTS_ENABLED, true),
            events = events,
        )

        // Position Settings
        val positionSettings = PositionSettings(
            horizontalOffset = prefs.getFloat(KEY_HORIZONTAL_OFFSET, 0f),
            verticalOffset = prefs.getFloat(KEY_VERTICAL_OFFSET, 0f),
            scale = prefs.getFloat(KEY_SCALE, 1.0f)
        )

        // Animation Settings
        val animationSettings = AnimationSettings(
            enabled = prefs.getBoolean(KEY_ANIMATION_ENABLED, false),
            type = enumPref(KEY_ANIMATION_TYPE, AnimationType.NONE),
            speed = prefs.getFloat(KEY_ANIMATION_SPEED, 1.0f),
            intensity = prefs.getFloat(KEY_ANIMATION_INTENSITY, 0.5f)
        )

        // Glass Effect Settings
        val glassEffectSettings = GlassEffectSettings(
            enabled = prefs.getBoolean(KEY_GLASS_ENABLED, false),
            style = enumPref(KEY_GLASS_STYLE, GlassStyle.NONE),
            blur = prefs.getFloat(KEY_GLASS_BLUR, 10f),
            opacity = prefs.getFloat(KEY_GLASS_OPACITY, 0.3f),
            tint = prefs.getInt(KEY_GLASS_TINT, 0x80FFFFFF.toInt())
        )

        // Tree Effect Settings
        val treeEffectSettings = TreeEffectSettings(
            enabled = prefs.getBoolean(KEY_TREE_ENABLED, false),
            style = enumPref(KEY_TREE_STYLE, TreeStyle.SIMPLE),
            trunkColor = prefs.getInt(KEY_TREE_TRUNK_COLOR, 0xFF8B4513.toInt()),
            leafColor = prefs.getInt(KEY_TREE_LEAF_COLOR, 0xFF228B22.toInt()),
            bloomColor = prefs.getInt(KEY_TREE_BLOOM_COLOR, 0xFFFF69B4.toInt()),
            showGround = prefs.getBoolean(KEY_TREE_SHOW_GROUND, true)
        )

        // Fluid Effect Settings
        val fluidEffectSettings = FluidEffectSettings(
            enabled = prefs.getBoolean(KEY_FLUID_ENABLED, false),
            style = enumPref(KEY_FLUID_STYLE, FluidStyle.NONE),
            flowSpeed = prefs.getFloat(KEY_FLUID_FLOW_SPEED, 1.0f),
            turbulence = prefs.getFloat(KEY_FLUID_TURBULENCE, 0.5f),
            colorIntensity = prefs.getFloat(KEY_FLUID_COLOR_INTENSITY, 0.7f)
        )

        val visualTheme = enumPref(KEY_VISUAL_THEME, VisualTheme.CLASSIC)

        return WallpaperSettings(
            theme = enumPref(KEY_THEME, ThemeOption.AMOLED),
            dotSize = enumPref(KEY_DOT_SIZE, DotSize.MEDIUM),
            dotShape = enumPref(KEY_DOT_SHAPE, DotShape.CIRCLE),
            gridDensity = enumPref(KEY_GRID_DENSITY, GridDensity.COMPACT),
            highlightToday = prefs.getBoolean(KEY_HIGHLIGHT_TODAY, true),
            filledDotAlpha = prefs.getFloat(KEY_FILLED_DOT_ALPHA, 1.0f),
            yilStatsBandOffset = prefs.getFloat(KEY_YIL_STATS_OFFSET, 0f),
            emptyDotAlpha = prefs.getFloat(KEY_EMPTY_DOT_ALPHA, 1.0f),
            customColors = customColors,
            dotEffectSettings = dotEffectSettings,
            footerTextSettings = footerTextSettings,
            viewModeSettings = viewModeSettings,
            calendarViewSettings = calendarViewSettings,
            goalSettings = goalSettings,
            eventSettings = eventSettings,
            positionSettings = positionSettings,
            animationSettings = animationSettings,
            glassEffectSettings = glassEffectSettings,
            treeEffectSettings = treeEffectSettings,
            fluidEffectSettings = fluidEffectSettings,
            visualTheme = visualTheme,
            topViewMode = enumPref(KEY_TOP_VIEW_MODE, TopViewMode.YIL),
            autoSwitchSettings = AutoSwitchSettings(
                enabled = prefs.getBoolean(KEY_AUTO_SWITCH_ENABLED, false),
                intervalMs = prefs.getLong(KEY_AUTO_SWITCH_INTERVAL_MS, 5_000L),
                referenceMs = prefs.getLong(KEY_AUTO_SWITCH_REFERENCE_MS, 0L),
                startMode = enumPref(KEY_AUTO_SWITCH_START_MODE, TopViewMode.YIL),
            ),
            umrSettings = UmrSettings(
                birthdayEpochMs = prefs.getLong(KEY_UMR_BIRTHDAY_MS, 0L),
                momBirthdayEpochMs = prefs.getLong(KEY_UMR_MOM_BIRTHDAY_MS, 0L),
                dadBirthdayEpochMs = prefs.getLong(KEY_UMR_DAD_BIRTHDAY_MS, 0L),
                visualMode = enumPref(KEY_UMR_VISUAL_MODE, UmrVisualMode.DOTS),
                livedAlpha = prefs.getFloat(KEY_UMR_LIVED_ALPHA, 1.0f),
                emptyAlpha = prefs.getFloat(KEY_UMR_EMPTY_ALPHA, 0.6f),
                totalWeeks = prefs.getInt(KEY_UMR_TOTAL_WEEKS, 4000),
                position = PositionSettings(
                    horizontalOffset = prefs.getFloat(KEY_UMR_HORIZONTAL_OFFSET, 0f),
                    verticalOffset = prefs.getFloat(KEY_UMR_VERTICAL_OFFSET, 7f),
                    scale = prefs.getFloat(KEY_UMR_SCALE, 1.0f),
                ),
                statsBandOffset = prefs.getFloat(KEY_UMR_STATS_OFFSET, 0f),
            ),
            soundsEnabled = prefs.getBoolean(KEY_SOUNDS_ENABLED, true),
            vibrationsEnabled = prefs.getBoolean(KEY_VIBRATIONS_ENABLED, true),
            applyMode = enumPref(KEY_APPLY_MODE, WallpaperApplyMode.BOTH_LIVE),
        )
    }

    fun setTheme(theme: ThemeOption) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(theme = theme)
        notifyWallpaperChanged()
    }

    fun setDotSize(size: DotSize) {
        prefs.edit().putString(KEY_DOT_SIZE, size.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(dotSize = size)
        notifyWallpaperChanged()
    }

    fun setDotShape(shape: DotShape) {
        prefs.edit().putString(KEY_DOT_SHAPE, shape.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(dotShape = shape)
        notifyWallpaperChanged()
    }

    fun setGridDensity(density: GridDensity) {
        prefs.edit().putString(KEY_GRID_DENSITY, density.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(gridDensity = density)
        notifyWallpaperChanged()
    }

    fun setHighlightToday(highlight: Boolean) {
        prefs.edit().putBoolean(KEY_HIGHLIGHT_TODAY, highlight).apply()
        _settingsFlow.value = _settingsFlow.value.copy(highlightToday = highlight)
        notifyWallpaperChanged()
    }

    fun setFilledDotAlpha(alpha: Float) {
        prefs.edit().putFloat(KEY_FILLED_DOT_ALPHA, alpha).apply()
        _settingsFlow.value = _settingsFlow.value.copy(filledDotAlpha = alpha)
        notifyWallpaperChanged()
    }

    fun setEmptyDotAlpha(alpha: Float) {
        prefs.edit().putFloat(KEY_EMPTY_DOT_ALPHA, alpha).apply()
        _settingsFlow.value = _settingsFlow.value.copy(emptyDotAlpha = alpha)
        notifyWallpaperChanged()
    }

    fun setCustomBackgroundColor(color: Int) {
        prefs.edit().putInt(KEY_CUSTOM_BG_COLOR, color).apply()
        val newCustomColors = _settingsFlow.value.customColors.copy(backgroundColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(customColors = newCustomColors)
        notifyWallpaperChanged()
    }

    fun setCustomFilledDotColor(color: Int) {
        prefs.edit().putInt(KEY_CUSTOM_FILLED_COLOR, color).apply()
        val newCustomColors = _settingsFlow.value.customColors.copy(filledDotColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(customColors = newCustomColors)
        notifyWallpaperChanged()
    }

    fun setCustomEmptyDotColor(color: Int) {
        prefs.edit().putInt(KEY_CUSTOM_EMPTY_COLOR, color).apply()
        val newCustomColors = _settingsFlow.value.customColors.copy(emptyDotColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(customColors = newCustomColors)
        notifyWallpaperChanged()
    }

    fun setCustomTodayDotColor(color: Int) {
        prefs.edit().putInt(KEY_CUSTOM_TODAY_COLOR, color).apply()
        val newCustomColors = _settingsFlow.value.customColors.copy(todayDotColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(customColors = newCustomColors)
        notifyWallpaperChanged()
    }

    // Feature 3: Dot Effects setters
    fun setDotStyle(style: DotStyle) {
        prefs.edit().putString(KEY_DOT_STYLE, style.name).apply()
        val newDotEffects = _settingsFlow.value.dotEffectSettings.copy(style = style)
        _settingsFlow.value = _settingsFlow.value.copy(dotEffectSettings = newDotEffects)
        notifyWallpaperChanged()
    }

    fun setGlowRadius(radius: Float) {
        prefs.edit().putFloat(KEY_GLOW_RADIUS, radius).apply()
        val newDotEffects = _settingsFlow.value.dotEffectSettings.copy(glowRadius = radius)
        _settingsFlow.value = _settingsFlow.value.copy(dotEffectSettings = newDotEffects)
        notifyWallpaperChanged()
    }

    fun setOutlineWidth(width: Float) {
        prefs.edit().putFloat(KEY_OUTLINE_WIDTH, width).apply()
        val newDotEffects = _settingsFlow.value.dotEffectSettings.copy(outlineWidth = width)
        _settingsFlow.value = _settingsFlow.value.copy(dotEffectSettings = newDotEffects)
        notifyWallpaperChanged()
    }

    // Feature 2: Footer Text setters
    fun setFooterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FOOTER_ENABLED, enabled).apply()
        val newFooter = _settingsFlow.value.footerTextSettings.copy(enabled = enabled)
        _settingsFlow.value = _settingsFlow.value.copy(footerTextSettings = newFooter)
        notifyWallpaperChanged()
    }

    fun setFooterText(text: String) {
        prefs.edit().putString(KEY_FOOTER_TEXT, text).apply()
        val newFooter = _settingsFlow.value.footerTextSettings.copy(text = text)
        _settingsFlow.value = _settingsFlow.value.copy(footerTextSettings = newFooter)
        notifyWallpaperChanged()
    }

    fun setFooterFontSize(size: Float) {
        prefs.edit().putFloat(KEY_FOOTER_FONT_SIZE, size).apply()
        val newFooter = _settingsFlow.value.footerTextSettings.copy(fontSize = size)
        _settingsFlow.value = _settingsFlow.value.copy(footerTextSettings = newFooter)
        notifyWallpaperChanged()
    }

    fun setFooterColor(color: Int) {
        prefs.edit().putInt(KEY_FOOTER_COLOR, color).apply()
        val newFooter = _settingsFlow.value.footerTextSettings.copy(color = color)
        _settingsFlow.value = _settingsFlow.value.copy(footerTextSettings = newFooter)
        notifyWallpaperChanged()
    }

    fun setFooterAlignment(alignment: TextAlignment) {
        prefs.edit().putString(KEY_FOOTER_ALIGNMENT, alignment.name).apply()
        val newFooter = _settingsFlow.value.footerTextSettings.copy(alignment = alignment)
        _settingsFlow.value = _settingsFlow.value.copy(footerTextSettings = newFooter)
        notifyWallpaperChanged()
    }

    // Features 4 & 5: View Mode setters
    fun setViewMode(mode: ViewMode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
        val newViewMode = _settingsFlow.value.viewModeSettings.copy(mode = mode)
        _settingsFlow.value = _settingsFlow.value.copy(viewModeSettings = newViewMode)
        notifyWallpaperChanged()
    }

    fun setShowMonthLabels(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MONTH_LABELS, show).apply()
        val newViewMode = _settingsFlow.value.viewModeSettings.copy(showMonthLabels = show)
        _settingsFlow.value = _settingsFlow.value.copy(viewModeSettings = newViewMode)
        notifyWallpaperChanged()
    }

    fun setMonthLabelColor(color: Int) {
        prefs.edit().putInt(KEY_MONTH_LABEL_COLOR, color).apply()
        val newViewMode = _settingsFlow.value.viewModeSettings.copy(monthLabelColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(viewModeSettings = newViewMode)
        notifyWallpaperChanged()
    }

    fun setCalendarMondayFirst(mondayFirst: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR_MONDAY_FIRST, mondayFirst).apply()
        _settingsFlow.value = settings.copy(
            calendarViewSettings = settings.calendarViewSettings.copy(mondayFirst = mondayFirst)
        )
        notifyWallpaperChanged()
    }

    fun setCalendarColumns(columns: Int) {
        prefs.edit().putInt(KEY_CALENDAR_COLUMNS, columns).apply()
        val newCalendar = _settingsFlow.value.calendarViewSettings.copy(columnsPerRow = columns)
        _settingsFlow.value = _settingsFlow.value.copy(calendarViewSettings = newCalendar)
        notifyWallpaperChanged()
    }

    // Feature 6: Goal Tracking setters
    fun setGoalsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GOALS_ENABLED, enabled).apply()
        val newGoals = _settingsFlow.value.goalSettings.copy(enabled = enabled)
        _settingsFlow.value = _settingsFlow.value.copy(goalSettings = newGoals)
        notifyWallpaperChanged()
    }

    fun setGoalsPosition(position: GoalPosition) {
        prefs.edit().putString(KEY_GOALS_POSITION, position.name).apply()
        val newGoals = _settingsFlow.value.goalSettings.copy(position = position)
        _settingsFlow.value = _settingsFlow.value.copy(goalSettings = newGoals)
        notifyWallpaperChanged()
    }

    fun addGoal(goal: Goal) {
        val currentGoals = _settingsFlow.value.goalSettings.goals.toMutableList()
        currentGoals.add(goal)
        saveGoals(currentGoals)
    }

    fun updateGoal(goal: Goal) {
        val currentGoals = _settingsFlow.value.goalSettings.goals.toMutableList()
        val index = currentGoals.indexOfFirst { it.id == goal.id }
        if (index >= 0) {
            currentGoals[index] = goal
            saveGoals(currentGoals)
        }
    }

    fun deleteGoal(goalId: String) {
        val currentGoals = _settingsFlow.value.goalSettings.goals.toMutableList()
        currentGoals.removeAll { it.id == goalId }
        saveGoals(currentGoals)
    }

    private fun saveGoals(goals: List<Goal>) {
        val goalsJson = gson.toJson(goals)
        prefs.edit().putString(KEY_GOALS_JSON, goalsJson).apply()
        val newGoalSettings = _settingsFlow.value.goalSettings.copy(goals = goals)
        _settingsFlow.value = _settingsFlow.value.copy(goalSettings = newGoalSettings)
        notifyWallpaperChanged()
    }

    // ===== Umr Event setters (parallel to Goal setters above) =====
    fun setEventsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EVENTS_ENABLED, enabled).apply()
        val newEvents = _settingsFlow.value.eventSettings.copy(enabled = enabled)
        _settingsFlow.value = _settingsFlow.value.copy(eventSettings = newEvents)
        notifyWallpaperChanged()
    }

    fun addEvent(event: Event) {
        val list = _settingsFlow.value.eventSettings.events.toMutableList()
        list.add(event)
        saveEvents(list)
    }

    fun updateEvent(event: Event) {
        val list = _settingsFlow.value.eventSettings.events.toMutableList()
        val index = list.indexOfFirst { it.id == event.id }
        if (index >= 0) {
            list[index] = event
            saveEvents(list)
        }
    }

    fun deleteEvent(eventId: String) {
        val list = _settingsFlow.value.eventSettings.events.toMutableList()
        list.removeAll { it.id == eventId }
        saveEvents(list)
    }

    private fun saveEvents(events: List<Event>) {
        val json = gson.toJson(events)
        prefs.edit().putString(KEY_EVENTS_JSON, json).apply()
        val newEventSettings = _settingsFlow.value.eventSettings.copy(events = events)
        _settingsFlow.value = _settingsFlow.value.copy(eventSettings = newEventSettings)
        notifyWallpaperChanged()
    }

    // ===== Position Settings setters =====
    fun setHorizontalOffset(offset: Float) {
        prefs.edit().putFloat(KEY_HORIZONTAL_OFFSET, offset).apply()
        val newPosition = _settingsFlow.value.positionSettings.copy(horizontalOffset = offset)
        _settingsFlow.value = _settingsFlow.value.copy(positionSettings = newPosition)
        notifyWallpaperChanged()
    }

    fun setVerticalOffset(offset: Float) {
        prefs.edit().putFloat(KEY_VERTICAL_OFFSET, offset).apply()
        val newPosition = _settingsFlow.value.positionSettings.copy(verticalOffset = offset)
        _settingsFlow.value = _settingsFlow.value.copy(positionSettings = newPosition)
        notifyWallpaperChanged()
    }

    fun setScale(scale: Float) {
        prefs.edit().putFloat(KEY_SCALE, scale).apply()
        val newPosition = _settingsFlow.value.positionSettings.copy(scale = scale)
        _settingsFlow.value = _settingsFlow.value.copy(positionSettings = newPosition)
        notifyWallpaperChanged()
    }

    // ===== Umr-only Position setters =====
    fun setUmrHorizontalOffset(offset: Float) {
        prefs.edit().putFloat(KEY_UMR_HORIZONTAL_OFFSET, offset).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(
                position = current.umrSettings.position.copy(horizontalOffset = offset)
            )
        )
        notifyWallpaperChanged()
    }

    fun setUmrVerticalOffset(offset: Float) {
        prefs.edit().putFloat(KEY_UMR_VERTICAL_OFFSET, offset).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(
                position = current.umrSettings.position.copy(verticalOffset = offset)
            )
        )
        notifyWallpaperChanged()
    }

    fun setUmrScale(scale: Float) {
        prefs.edit().putFloat(KEY_UMR_SCALE, scale).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(
                position = current.umrSettings.position.copy(scale = scale)
            )
        )
        notifyWallpaperChanged()
    }

    fun setUmrStatsBandOffset(offset: Float) {
        prefs.edit().putFloat(KEY_UMR_STATS_OFFSET, offset).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(statsBandOffset = offset)
        )
        notifyWallpaperChanged()
    }

    fun setYilStatsBandOffset(offset: Float) {
        prefs.edit().putFloat(KEY_YIL_STATS_OFFSET, offset).apply()
        _settingsFlow.value = _settingsFlow.value.copy(yilStatsBandOffset = offset)
        notifyWallpaperChanged()
    }

    // ===== Animation Settings setters =====
    fun setAnimationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATION_ENABLED, enabled).apply()
        val newAnimation = _settingsFlow.value.animationSettings.copy(enabled = enabled)
        _settingsFlow.value = _settingsFlow.value.copy(animationSettings = newAnimation)
        notifyWallpaperChanged()
    }

    fun setAnimationType(type: AnimationType) {
        prefs.edit().putString(KEY_ANIMATION_TYPE, type.name).apply()
        val newAnimation = _settingsFlow.value.animationSettings.copy(type = type)
        _settingsFlow.value = _settingsFlow.value.copy(animationSettings = newAnimation)
        notifyWallpaperChanged()
    }

    fun setAnimationSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_ANIMATION_SPEED, speed).apply()
        val newAnimation = _settingsFlow.value.animationSettings.copy(speed = speed)
        _settingsFlow.value = _settingsFlow.value.copy(animationSettings = newAnimation)
        notifyWallpaperChanged()
    }

    fun setAnimationIntensity(intensity: Float) {
        prefs.edit().putFloat(KEY_ANIMATION_INTENSITY, intensity).apply()
        val newAnimation = _settingsFlow.value.animationSettings.copy(intensity = intensity)
        _settingsFlow.value = _settingsFlow.value.copy(animationSettings = newAnimation)
        notifyWallpaperChanged()
    }

    // ===== Glass Effect Settings setters =====
    fun setGlassEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLASS_ENABLED, enabled).apply()
        val newGlass = _settingsFlow.value.glassEffectSettings.copy(enabled = enabled)
        _settingsFlow.value = _settingsFlow.value.copy(glassEffectSettings = newGlass)
        notifyWallpaperChanged()
    }

    fun setGlassStyle(style: GlassStyle) {
        prefs.edit().putString(KEY_GLASS_STYLE, style.name).apply()
        val newGlass = _settingsFlow.value.glassEffectSettings.copy(style = style)
        _settingsFlow.value = _settingsFlow.value.copy(glassEffectSettings = newGlass)
        notifyWallpaperChanged()
    }

    fun setGlassBlur(blur: Float) {
        prefs.edit().putFloat(KEY_GLASS_BLUR, blur).apply()
        val newGlass = _settingsFlow.value.glassEffectSettings.copy(blur = blur)
        _settingsFlow.value = _settingsFlow.value.copy(glassEffectSettings = newGlass)
        notifyWallpaperChanged()
    }

    fun setGlassOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_GLASS_OPACITY, opacity).apply()
        val newGlass = _settingsFlow.value.glassEffectSettings.copy(opacity = opacity)
        _settingsFlow.value = _settingsFlow.value.copy(glassEffectSettings = newGlass)
        notifyWallpaperChanged()
    }

    fun setGlassTint(tint: Int) {
        prefs.edit().putInt(KEY_GLASS_TINT, tint).apply()
        val newGlass = _settingsFlow.value.glassEffectSettings.copy(tint = tint)
        _settingsFlow.value = _settingsFlow.value.copy(glassEffectSettings = newGlass)
        notifyWallpaperChanged()
    }

    // ===== Tree Effect Settings setters =====
    fun setTreeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TREE_ENABLED, enabled).apply()
        val newTree = _settingsFlow.value.treeEffectSettings.copy(enabled = enabled)
        _settingsFlow.value = _settingsFlow.value.copy(treeEffectSettings = newTree)
        notifyWallpaperChanged()
    }

    fun setTreeStyle(style: TreeStyle) {
        prefs.edit().putString(KEY_TREE_STYLE, style.name).apply()
        val newTree = _settingsFlow.value.treeEffectSettings.copy(style = style)
        _settingsFlow.value = _settingsFlow.value.copy(treeEffectSettings = newTree)
        notifyWallpaperChanged()
    }

    fun setTreeTrunkColor(color: Int) {
        prefs.edit().putInt(KEY_TREE_TRUNK_COLOR, color).apply()
        val newTree = _settingsFlow.value.treeEffectSettings.copy(trunkColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(treeEffectSettings = newTree)
        notifyWallpaperChanged()
    }

    fun setTreeLeafColor(color: Int) {
        prefs.edit().putInt(KEY_TREE_LEAF_COLOR, color).apply()
        val newTree = _settingsFlow.value.treeEffectSettings.copy(leafColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(treeEffectSettings = newTree)
        notifyWallpaperChanged()
    }

    fun setTreeBloomColor(color: Int) {
        prefs.edit().putInt(KEY_TREE_BLOOM_COLOR, color).apply()
        val newTree = _settingsFlow.value.treeEffectSettings.copy(bloomColor = color)
        _settingsFlow.value = _settingsFlow.value.copy(treeEffectSettings = newTree)
        notifyWallpaperChanged()
    }

    fun setTreeShowGround(show: Boolean) {
        prefs.edit().putBoolean(KEY_TREE_SHOW_GROUND, show).apply()
        val newTree = _settingsFlow.value.treeEffectSettings.copy(showGround = show)
        _settingsFlow.value = _settingsFlow.value.copy(treeEffectSettings = newTree)
        notifyWallpaperChanged()
    }

    // ===== Fluid Effect Settings setters =====
    fun setFluidEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLUID_ENABLED, enabled).apply()
        val newFluid = _settingsFlow.value.fluidEffectSettings.copy(enabled = enabled)
        _settingsFlow.value = _settingsFlow.value.copy(fluidEffectSettings = newFluid)
        notifyWallpaperChanged()
    }

    fun setFluidStyle(style: FluidStyle) {
        prefs.edit().putString(KEY_FLUID_STYLE, style.name).apply()
        val newFluid = _settingsFlow.value.fluidEffectSettings.copy(style = style)
        _settingsFlow.value = _settingsFlow.value.copy(fluidEffectSettings = newFluid)
        notifyWallpaperChanged()
    }

    fun setFluidFlowSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_FLUID_FLOW_SPEED, speed).apply()
        val newFluid = _settingsFlow.value.fluidEffectSettings.copy(flowSpeed = speed)
        _settingsFlow.value = _settingsFlow.value.copy(fluidEffectSettings = newFluid)
        notifyWallpaperChanged()
    }

    fun setFluidTurbulence(turbulence: Float) {
        prefs.edit().putFloat(KEY_FLUID_TURBULENCE, turbulence).apply()
        val newFluid = _settingsFlow.value.fluidEffectSettings.copy(turbulence = turbulence)
        _settingsFlow.value = _settingsFlow.value.copy(fluidEffectSettings = newFluid)
        notifyWallpaperChanged()
    }

    fun setFluidColorIntensity(intensity: Float) {
        prefs.edit().putFloat(KEY_FLUID_COLOR_INTENSITY, intensity).apply()
        val newFluid = _settingsFlow.value.fluidEffectSettings.copy(colorIntensity = intensity)
        _settingsFlow.value = _settingsFlow.value.copy(fluidEffectSettings = newFluid)
        notifyWallpaperChanged()
    }

    // ===== Visual Theme setter =====
    fun setVisualTheme(theme: VisualTheme) {
        prefs.edit().putString(KEY_VISUAL_THEME, theme.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(visualTheme = theme)
        notifyWallpaperChanged()
    }

    // ===== Umr / TopViewMode / Auto-switch setters =====
    fun setTopViewMode(mode: TopViewMode) {
        val selected = selectCalendar(settings, mode, System.currentTimeMillis())
        prefs.edit()
            .putString(KEY_TOP_VIEW_MODE, mode.name)
            .putLong(KEY_AUTO_SWITCH_REFERENCE_MS, selected.autoSwitchSettings.referenceMs)
            .putString(KEY_AUTO_SWITCH_START_MODE, mode.name)
            .apply()
        _settingsFlow.value = selected
        notifyWallpaperChanged()
    }

    /**
     * Enable/disable the wall-clock-driven auto-switch.
     *
     * On enable, snapshot the current pick + wall-clock time into the
     * "reference" so the first interval starts on whichever side the
     * user is currently viewing. On disable, the formula falls back to
     * `topViewMode` (no state-clearing needed besides the flag).
     */
    fun setAutoSwitchEnabled(enabled: Boolean) {
        val current = _settingsFlow.value
        val newAuto = if (enabled) {
            current.autoSwitchSettings.copy(
                enabled = true,
                referenceMs = System.currentTimeMillis(),
                startMode = current.topViewMode,
            )
        } else {
            current.autoSwitchSettings.copy(enabled = false)
        }
        prefs.edit()
            .putBoolean(KEY_AUTO_SWITCH_ENABLED, newAuto.enabled)
            .putLong(KEY_AUTO_SWITCH_REFERENCE_MS, newAuto.referenceMs)
            .putString(KEY_AUTO_SWITCH_START_MODE, newAuto.startMode.name)
            .apply()
        _settingsFlow.value = current.copy(autoSwitchSettings = newAuto)
        notifyWallpaperChanged()
    }

    /**
     * Set the auto-switch interval. Also resets reference to "now" so
     * the new interval starts cleanly from the currently-rendering mode.
     */
    fun setAutoSwitchIntervalMs(intervalMs: Long) {
        val current = _settingsFlow.value
        val now = System.currentTimeMillis()
        val nowMode = currentEffectiveMode(now, current)
        val newAuto = current.autoSwitchSettings.copy(
            intervalMs = intervalMs,
            referenceMs = now,
            startMode = nowMode,
        )
        prefs.edit()
            .putLong(KEY_AUTO_SWITCH_INTERVAL_MS, newAuto.intervalMs)
            .putLong(KEY_AUTO_SWITCH_REFERENCE_MS, newAuto.referenceMs)
            .putString(KEY_AUTO_SWITCH_START_MODE, newAuto.startMode.name)
            .apply()
        _settingsFlow.value = current.copy(autoSwitchSettings = newAuto)
        notifyWallpaperChanged()
    }

    fun setUmrBirthday(epochMs: Long) {
        prefs.edit().putLong(KEY_UMR_BIRTHDAY_MS, epochMs).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(birthdayEpochMs = epochMs)
        )
        notifyWallpaperChanged()
    }

    fun setUmrMomBirthday(epochMs: Long) {
        prefs.edit().putLong(KEY_UMR_MOM_BIRTHDAY_MS, epochMs).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(momBirthdayEpochMs = epochMs)
        )
        notifyWallpaperChanged()
    }

    fun setUmrDadBirthday(epochMs: Long) {
        prefs.edit().putLong(KEY_UMR_DAD_BIRTHDAY_MS, epochMs).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(dadBirthdayEpochMs = epochMs)
        )
        notifyWallpaperChanged()
    }

    fun setUmrLivedAlpha(alpha: Float) {
        val v = alpha.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_UMR_LIVED_ALPHA, v).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(livedAlpha = v)
        )
        notifyWallpaperChanged()
    }

    fun setUmrEmptyAlpha(alpha: Float) {
        val v = alpha.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_UMR_EMPTY_ALPHA, v).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(emptyAlpha = v)
        )
        notifyWallpaperChanged()
    }

    /**
     * Atomic mode toggle: writes visualMode AND the mode's default alpha pair
     * in a single edit, so the UI can't observe a half-updated state.
     */
    fun setUmrVisualMode(mode: UmrVisualMode) {
        val (lived, empty) = when (mode) {
            UmrVisualMode.DOTS -> 1.0f to 0.6f
            UmrVisualMode.X_MARKS -> 0.30f to 1.0f
        }
        prefs.edit()
            .putString(KEY_UMR_VISUAL_MODE, mode.name)
            .putFloat(KEY_UMR_LIVED_ALPHA, lived)
            .putFloat(KEY_UMR_EMPTY_ALPHA, empty)
            .apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(
                visualMode = mode,
                livedAlpha = lived,
                emptyAlpha = empty,
            )
        )
        notifyWallpaperChanged()
    }

    fun setSoundsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUNDS_ENABLED, enabled).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(soundsEnabled = enabled)
    }

    fun setVibrationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATIONS_ENABLED, enabled).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(vibrationsEnabled = enabled)
    }

    fun setApplyMode(mode: WallpaperApplyMode) {
        prefs.edit().putString(KEY_APPLY_MODE, mode.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(applyMode = mode)
    }

    fun notifyWallpaperChanged() {
        wallpaperChangeListeners.forEach { it.invoke() }
    }

    companion object {
        private const val PREFS_NAME = "lifedots_prefs"

        // Schema migration version. Bump and add a branch in runMigrationsIfNeeded()
        // whenever defaults change in a way that needs to override existing
        // saved values (e.g., the rebrand from LifeDots default CONTINUOUS to
        // O'lyapmiz default CALENDAR).
        private const val KEY_MIGRATION_VERSION = "migration_version"
        private const val CURRENT_MIGRATION_VERSION = 12

        private const val KEY_THEME = "theme"
        private const val KEY_DOT_SIZE = "dot_size"
        private const val KEY_DOT_SHAPE = "dot_shape"
        private const val KEY_GRID_DENSITY = "grid_density"
        private const val KEY_HIGHLIGHT_TODAY = "highlight_today"
        private const val KEY_FILLED_DOT_ALPHA = "filled_dot_alpha"
        private const val KEY_EMPTY_DOT_ALPHA = "empty_dot_alpha"
        private const val KEY_CUSTOM_BG_COLOR = "custom_bg_color"
        private const val KEY_CUSTOM_FILLED_COLOR = "custom_filled_color"
        private const val KEY_CUSTOM_EMPTY_COLOR = "custom_empty_color"
        private const val KEY_CUSTOM_TODAY_COLOR = "custom_today_color"

        // Feature 3: Dot Effects keys
        private const val KEY_DOT_STYLE = "dot_style"
        private const val KEY_GLOW_RADIUS = "glow_radius"
        private const val KEY_OUTLINE_WIDTH = "outline_width"

        // Feature 2: Footer Text keys
        private const val KEY_FOOTER_ENABLED = "footer_enabled"
        private const val KEY_FOOTER_TEXT = "footer_text"
        private const val KEY_FOOTER_FONT_SIZE = "footer_font_size"
        private const val KEY_FOOTER_COLOR = "footer_color"
        private const val KEY_FOOTER_ALIGNMENT = "footer_alignment"

        // Features 4 & 5: View Mode keys
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_SHOW_MONTH_LABELS = "show_month_labels"
        private const val KEY_MONTH_LABEL_COLOR = "month_label_color"
        private const val KEY_CALENDAR_COLUMNS = "calendar_columns"
        private const val KEY_CALENDAR_STATS = "calendar_stats"
        private const val KEY_CALENDAR_MONDAY_FIRST = "calendar_monday_first"
        private const val KEY_CALENDAR_HIGHLIGHT_WEEK = "calendar_highlight_week"
        private const val KEY_CALENDAR_WEEK_COLOR = "calendar_week_color"
        private const val KEY_CALENDAR_EVENT_ENABLED = "calendar_event_enabled"
        private const val KEY_CALENDAR_EVENT_MONTH = "calendar_event_month"
        private const val KEY_CALENDAR_EVENT_DAY = "calendar_event_day"
        private const val KEY_CALENDAR_EVENT_LABEL = "calendar_event_label"
        private const val KEY_CALENDAR_EVENT_COLOR = "calendar_event_color"

        // Feature 6: Goal Tracking keys
        private const val KEY_GOALS_ENABLED = "goals_enabled"
        private const val KEY_GOALS_JSON = "goals_json"
        private const val KEY_GOALS_POSITION = "goals_position"

        // Umr Event Tracking keys (separate from goals — events can be past or future)
        private const val KEY_EVENTS_ENABLED = "umr_events_enabled"
        private const val KEY_EVENTS_JSON = "umr_events_json"

        // Umr position keys (independent of Yil's positionSettings)
        private const val KEY_UMR_HORIZONTAL_OFFSET = "umr_horizontal_offset"
        private const val KEY_UMR_VERTICAL_OFFSET = "umr_vertical_offset"
        private const val KEY_UMR_SCALE = "umr_scale"
        private const val KEY_UMR_STATS_OFFSET = "umr_stats_offset"
        private const val KEY_YIL_STATS_OFFSET = "yil_stats_offset"

        // Position Settings keys
        private const val KEY_HORIZONTAL_OFFSET = "horizontal_offset"
        private const val KEY_VERTICAL_OFFSET = "vertical_offset"
        private const val KEY_SCALE = "scale"

        // Animation Settings keys
        private const val KEY_ANIMATION_ENABLED = "animation_enabled"
        private const val KEY_ANIMATION_TYPE = "animation_type"
        private const val KEY_ANIMATION_SPEED = "animation_speed"
        private const val KEY_ANIMATION_INTENSITY = "animation_intensity"

        // Glass Effect Settings keys
        private const val KEY_GLASS_ENABLED = "glass_enabled"
        private const val KEY_GLASS_STYLE = "glass_style"
        private const val KEY_GLASS_BLUR = "glass_blur"
        private const val KEY_GLASS_OPACITY = "glass_opacity"
        private const val KEY_GLASS_TINT = "glass_tint"

        // Tree Effect Settings keys
        private const val KEY_TREE_ENABLED = "tree_enabled"
        private const val KEY_TREE_STYLE = "tree_style"
        private const val KEY_TREE_TRUNK_COLOR = "tree_trunk_color"
        private const val KEY_TREE_LEAF_COLOR = "tree_leaf_color"
        private const val KEY_TREE_BLOOM_COLOR = "tree_bloom_color"
        private const val KEY_TREE_SHOW_GROUND = "tree_show_ground"

        // Fluid Effect Settings keys
        private const val KEY_FLUID_ENABLED = "fluid_enabled"
        private const val KEY_FLUID_STYLE = "fluid_style"
        private const val KEY_FLUID_FLOW_SPEED = "fluid_flow_speed"
        private const val KEY_FLUID_TURBULENCE = "fluid_turbulence"
        private const val KEY_FLUID_COLOR_INTENSITY = "fluid_color_intensity"

        // Visual Theme key
        private const val KEY_VISUAL_THEME = "visual_theme"

        // Sounds and vibrations keys
        private const val KEY_SOUNDS_ENABLED = "sounds_enabled"
        private const val KEY_VIBRATIONS_ENABLED = "vibrations_enabled"

        // Top view mode + auto-switch + Umr keys
        private const val KEY_TOP_VIEW_MODE = "top_view_mode"
        private const val KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
        private const val KEY_AUTO_SWITCH_INTERVAL_MS = "auto_switch_interval_ms"
        private const val KEY_AUTO_SWITCH_REFERENCE_MS = "auto_switch_reference_ms"
        private const val KEY_AUTO_SWITCH_START_MODE = "auto_switch_start_mode"
        private const val KEY_UMR_BIRTHDAY_MS = "umr_birthday_ms"
        private const val KEY_UMR_MOM_BIRTHDAY_MS = "umr_mom_birthday_ms"
        private const val KEY_UMR_DAD_BIRTHDAY_MS = "umr_dad_birthday_ms"
        private const val KEY_UMR_VISUAL_MODE = "umr_visual_mode"
        private const val KEY_UMR_LIVED_ALPHA = "umr_lived_alpha"
        private const val KEY_UMR_EMPTY_ALPHA = "umr_empty_alpha"
        private const val KEY_UMR_TOTAL_WEEKS = "umr_total_weeks"

        // "Set as Wallpaper" branch: live engine on both screens (default)
        // vs render-to-bitmap → calendar on lock, black on home.
        private const val KEY_APPLY_MODE = "apply_mode"

        private val wallpaperChangeListeners = mutableListOf<() -> Unit>()

        fun addWallpaperChangeListener(listener: () -> Unit) {
            wallpaperChangeListeners.add(listener)
        }

        fun removeWallpaperChangeListener(listener: () -> Unit) {
            wallpaperChangeListeners.remove(listener)
        }

        @Volatile
        private var instance: LifeDotsPreferences? = null

        fun getInstance(context: Context): LifeDotsPreferences {
            return instance ?: synchronized(this) {
                instance ?: LifeDotsPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
