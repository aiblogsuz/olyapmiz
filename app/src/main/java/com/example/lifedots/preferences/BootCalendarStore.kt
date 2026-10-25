package com.example.lifedots.preferences

import android.content.Context
import android.os.UserManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** True only after Android makes credential-protected app storage available. */
fun isUserUnlocked(context: Context): Boolean =
    context.getSystemService(UserManager::class.java)?.isUserUnlocked == true

/** Never opens normal preferences before the user's first unlock after reboot. */
fun settingsForRendering(context: Context): WallpaperSettings? =
    if (isUserUnlocked(context)) LifeDotsPreferences.getInstance(context).settings
    else BootCalendarStore.read(context)

/**
 * A rendering-only copy. Credential-protected preferences remain authoritative;
 * this store is never restored or moved over them after unlock.
 */
object BootCalendarStore {
    internal const val STORE = "boot_calendar_snapshot"
    internal const val SNAPSHOT = "snapshot"
    private const val VERSION = 1
    private const val MAX_JSON_LENGTH = 1_000_000
    private val gson = Gson()
    private val settingsShape = gson.toJsonTree(WallpaperSettings())
    private val goalShape = gson.toJsonTree(Goal(id = "shape", title = "shape", targetDate = 0L))
    private val eventShape = gson.toJsonTree(Event(id = "shape", title = "shape", targetDate = 0L))

    private data class CachedSnapshot(val payload: String?, val settings: WallpaperSettings?)
    @Volatile private var cachedSnapshot: CachedSnapshot? = null

    /** Publishes from unlocked normal preferences only. SharedPreferences queues the disk write. */
    fun publish(context: Context, settings: WallpaperSettings) {
        if (!isUserUnlocked(context)) return
        runCatching {
            val store = context.createDeviceProtectedStorageContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val encoded = encode(settings)
            val editor = store.edit()
            // An invalid new state must not leave a stale, apparently current design behind.
            if (encoded == null) editor.remove(SNAPSHOT) else editor.putString(SNAPSHOT, encoded)
            editor.apply()
        }.onFailure { Log.w("LifeDots", "Could not publish boot calendar snapshot", it) }
    }

    /** Missing/corrupt mirrors return null so the locked renderer can display black. */
    fun read(context: Context): WallpaperSettings? = runCatching {
        val store = context.createDeviceProtectedStorageContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val payload = store.getString(SNAPSHOT, null)
        val cached = cachedSnapshot
        if (cached != null && cached.payload == payload) cached.settings
        else decode(payload).also { cachedSnapshot = CachedSnapshot(payload, it) }
    }.getOrNull()

    internal fun encode(settings: WallpaperSettings): String? = runCatching {
        val root = JsonObject().apply {
            addProperty("version", VERSION)
            add("settings", gson.toJsonTree(settings))
        }
        root.toString().takeIf { decode(it) != null }
    }.getOrNull()

    internal fun decode(json: String?): WallpaperSettings? = runCatching {
        if (json == null || json.length > MAX_JSON_LENGTH) return null
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("version")?.asJsonPrimitive ?: return null
        if (!version.isNumber || version.asBigDecimal.intValueExact() != VERSION) return null
        val value = root.get("settings") ?: return null
        if (!validShape(value, settingsShape, "")) return null
        if (!enumFields.all { (path, names) -> field(value, path).asString in names }) return null
        if (!ranges.all { (path, range) -> !featureEnabled(value, path) || field(value, path).asDouble in range }) return null
        for (path in listOf("autoSwitchSettings.referenceMs", "umrSettings.birthdayEpochMs",
            "umrSettings.momBirthdayEpochMs", "umrSettings.dadBirthdayEpochMs")) {
            if (featureEnabled(value, path) && !validEpoch(field(value, path).asLong)) return null
        }
        for (path in listOf("goalSettings.goals", "eventSettings.events")) {
            for (item in field(value, path).asJsonArray) {
                if (featureEnabled(value, path) && !validEpoch(item.asJsonObject["targetDate"].asLong)) return null
            }
        }
        gson.fromJson(value, WallpaperSettings::class.java)
    }.getOrNull()

    // Editor supports calendar years 1–9999. One day of margin preserves local dates across time zones.
    private fun validEpoch(value: Long): Boolean = value in -62_135_683_200_000L..253_402_387_199_999L

    private fun featureEnabled(root: JsonElement, path: String): Boolean = when {
        path.startsWith("calendarViewSettings.event") -> field(root, "calendarViewSettings.eventEnabled").asBoolean
        path.startsWith("autoSwitchSettings.") -> field(root, "autoSwitchSettings.enabled").asBoolean
        else -> {
            val group = path.substringBefore('.')
            if (group in setOf("animationSettings", "glassEffectSettings", "fluidEffectSettings",
                    "footerTextSettings", "goalSettings", "eventSettings")) field(root, "$group.enabled").asBoolean
            else true
        }
    }

    private fun field(root: JsonElement, path: String): JsonElement =
        path.split('.').fold(root) { value, key -> value.asJsonObject[key] }

    /** Validate complete required shape before Gson can inject null into Kotlin non-null fields. */
    private fun validShape(value: JsonElement?, shape: JsonElement, path: String): Boolean {
        if (value == null || value.isJsonNull) return false
        if (shape.isJsonObject) return value.isJsonObject && shape.asJsonObject.entrySet().all { (key, child) ->
            validShape(value.asJsonObject[key], child, if (path.isEmpty()) key else "$path.$key")
        }
        if (shape.isJsonArray) {
            if (!value.isJsonArray || value.asJsonArray.size() > 1024) return false
            val itemShape = when (path) {
                "goalSettings.goals" -> goalShape
                "eventSettings.events" -> eventShape
                else -> return false
            }
            return value.asJsonArray.all { validShape(it, itemShape, "$path[]") }
        }
        if (!value.isJsonPrimitive) return false
        val actual = value.asJsonPrimitive
        val expected = shape.asJsonPrimitive
        return when {
            expected.isBoolean -> actual.isBoolean
            expected.isString -> actual.isString && actual.asString.length <= 8192
            expected.isNumber -> actual.isNumber && runCatching {
                val number = actual.asBigDecimal
                when (expected.asNumber) {
                    is Int -> number.intValueExact()
                    is Long -> number.longValueExact()
                    else -> require(number.toDouble().isFinite())
                }
                true
            }.getOrDefault(false)
            else -> false
        }
    }

    private inline fun <reified T : Enum<T>> names() = enumValues<T>().map { it.name }.toSet()
    private val enumFields = mapOf(
        "theme" to names<ThemeOption>(), "dotSize" to names<DotSize>(), "dotShape" to names<DotShape>(),
        "gridDensity" to names<GridDensity>(), "dotEffectSettings.style" to names<DotStyle>(),
        "footerTextSettings.alignment" to names<TextAlignment>(), "viewModeSettings.mode" to names<ViewMode>(),
        "goalSettings.position" to names<GoalPosition>(), "animationSettings.type" to names<AnimationType>(),
        "glassEffectSettings.style" to names<GlassStyle>(), "treeEffectSettings.style" to names<TreeStyle>(),
        "fluidEffectSettings.style" to names<FluidStyle>(), "visualTheme" to names<VisualTheme>(),
        "topViewMode" to names<TopViewMode>(), "autoSwitchSettings.startMode" to names<TopViewMode>(),
        "umrSettings.visualMode" to names<UmrVisualMode>(), "applyMode" to names<WallpaperApplyMode>(),
    )
    private val ranges = buildMap<String, ClosedFloatingPointRange<Double>> {
        for (path in listOf("filledDotAlpha", "emptyDotAlpha", "umrSettings.livedAlpha", "umrSettings.emptyAlpha",
            "animationSettings.intensity", "glassEffectSettings.opacity", "fluidEffectSettings.turbulence",
            "fluidEffectSettings.colorIntensity")) put(path, 0.0..1.0)
        for (prefix in listOf("positionSettings", "umrSettings.position")) {
            put("$prefix.horizontalOffset", -100.0..100.0)
            put("$prefix.verticalOffset", -100.0..100.0)
            put("$prefix.scale", 0.05..5.0)
        }
        put("yilStatsBandOffset", -100.0..100.0); put("umrSettings.statsBandOffset", -100.0..100.0)
        put("dotEffectSettings.glowRadius", 0.0..100.0); put("dotEffectSettings.outlineWidth", 0.0..100.0)
        put("footerTextSettings.fontSize", 1.0..200.0); put("calendarViewSettings.columnsPerRow", 2.0..4.0)
        put("calendarViewSettings.eventMonth", 0.0..11.0); put("calendarViewSettings.eventDay", 1.0..31.0)
        put("autoSwitchSettings.intervalMs", 1000.0..86_400_000.0); put("umrSettings.totalWeeks", 1.0..10_000.0)
        put("animationSettings.speed", 0.01..100.0); put("glassEffectSettings.blur", 0.0..100.0)
        put("fluidEffectSettings.flowSpeed", 0.0..100.0)
    }
}
