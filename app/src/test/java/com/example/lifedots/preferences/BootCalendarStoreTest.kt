package com.example.lifedots.preferences

import android.app.Application
import android.content.SharedPreferences
import android.content.Context
import android.os.UserManager
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCalendarStoreTest {
    private lateinit var context: Application
    private fun unlock(value: Boolean) = shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(value)
    private lateinit var normalPrefs: SharedPreferences
    private fun normalStore() = normalPrefs
    private fun bootStore() = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(BootCalendarStore.STORE, Context.MODE_PRIVATE)
    private fun epoch(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun custom() = WallpaperSettings(
        theme = ThemeOption.CUSTOM, topViewMode = TopViewMode.UMR,
        customColors = CustomColors(backgroundColor = 0xFF221100.toInt(), filledDotColor = 0xFFEEDDCC.toInt()),
        viewModeSettings = ViewModeSettings(mode = ViewMode.MONTHLY, showMonthLabels = false),
        calendarViewSettings = CalendarViewSettings(columnsPerRow = 4, mondayFirst = false),
        positionSettings = PositionSettings(-8f, 12f, 0.8f),
        umrSettings = UmrSettings(birthdayEpochMs = epoch(1960, 5, 1), visualMode = UmrVisualMode.X_MARKS,
            position = PositionSettings(4f, -6f, 1.2f)),
        eventSettings = EventSettings(events = listOf(Event("history", "Historical event", epoch(1880, 4, 2)))),
        autoSwitchSettings = AutoSwitchSettings(enabled = false, startMode = TopViewMode.UMR),
    )

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        unlock(true)
        normalPrefs = context.getSharedPreferences("lifedots_prefs", Context.MODE_PRIVATE)
        ReflectionHelpers.setStaticField(LifeDotsPreferences::class.java, "instance", null)
        normalStore().edit().clear().commit()
        bootStore().edit().clear().commit()
    }

    @Test fun `typed snapshot round trip preserves custom layout colors negative epoch and selected mode`() {
        val selected = custom()
        val encoded = BootCalendarStore.encode(selected)
        assertNotNull(encoded)
        assertEquals(selected, BootCalendarStore.decode(encoded))
        BootCalendarStore.publish(context, selected)
        assertEquals(selected, BootCalendarStore.read(context))
        assertNull(context.getSharedPreferences(BootCalendarStore.STORE, Context.MODE_PRIVATE)
            .getString(BootCalendarStore.SNAPSHOT, null))
    }

    @Test fun `corrupt schemas enums null fields wrong primitive types and malformed lists are rejected`() {
        fun broken(change: (JsonObject) -> Unit): String {
            val root = JsonParser.parseString(BootCalendarStore.encode(custom())!!).asJsonObject
            change(root)
            return root.toString()
        }
        val invalid = listOf("not json", "null", "{}",
            broken { it.addProperty("version", 2) },
            broken { it.addProperty("version", 1.5) },
            broken { it.getAsJsonObject("settings").remove("customColors") },
            broken { it.getAsJsonObject("settings").add("umrSettings", JsonNull.INSTANCE) },
            broken { it.getAsJsonObject("settings").addProperty("topViewMode", "NOT_A_MODE") },
            broken { it.getAsJsonObject("settings").getAsJsonObject("viewModeSettings").addProperty("mode", "OTHER") },
            broken { it.getAsJsonObject("settings").addProperty("highlightToday", "true") },
            broken { it.getAsJsonObject("settings").getAsJsonObject("calendarViewSettings").addProperty("columnsPerRow", 2.5) },
            broken { it.getAsJsonObject("settings").getAsJsonObject("eventSettings").getAsJsonArray("events").add(JsonNull.INSTANCE) },
            broken { it.getAsJsonObject("settings").getAsJsonObject("eventSettings").getAsJsonArray("events")[0]
                .asJsonObject.add("title", JsonNull.INSTANCE) })
        invalid.forEach { assertNull("Accepted invalid snapshot: $it", BootCalendarStore.decode(it)) }
    }

    @Test fun `disabled optional ranges do not discard an otherwise valid calendar`() {
        val selected = custom().copy(
            autoSwitchSettings = AutoSwitchSettings(enabled = false, intervalMs = 0L),
            footerTextSettings = FooterTextSettings(enabled = false, fontSize = 0f),
            animationSettings = AnimationSettings(enabled = false, speed = 0f),
            calendarViewSettings = CalendarViewSettings(eventEnabled = false, eventMonth = -1, eventDay = 0),
        )
        assertEquals(selected, BootCalendarStore.decode(BootCalendarStore.encode(selected)))
        assertNull(BootCalendarStore.encode(selected.copy(filledDotAlpha = Float.NaN)))
    }

    @Test fun `locked provider reads only mirrored settings and changes invalidate its single entry cache`() {
        val first = custom()
        BootCalendarStore.publish(context, first)
        val normalBefore = normalStore().all.toMap()
        unlock(false)
        repeat(3) { assertEquals(first, settingsForRendering(context)) }
        assertNull(ReflectionHelpers.getStaticField<LifeDotsPreferences?>(LifeDotsPreferences::class.java, "instance"))
        val replacement = first.copy(topViewMode = TopViewMode.YIL, theme = ThemeOption.LIGHT)
        bootStore().edit().putString(BootCalendarStore.SNAPSHOT, BootCalendarStore.encode(replacement)).commit()
        assertEquals(replacement, settingsForRendering(context))
        bootStore().edit().putString(BootCalendarStore.SNAPSHOT, "corrupt").commit()
        assertNull(settingsForRendering(context))
        assertNull(settingsForRendering(context))
        bootStore().edit().putString(BootCalendarStore.SNAPSHOT, BootCalendarStore.encode(first)).commit()
        assertEquals(first, settingsForRendering(context))
        assertEquals(normalBefore, normalStore().all)
    }

    @Test fun `normal initialization publishes migrated CE settings and mutations keep mirror current`() {
        normalStore().edit().putString("view_mode", "MONTHLY").putBoolean("calendar_monday_first", false).commit()
        val prefs = LifeDotsPreferences(context)
        assertEquals(ViewMode.MONTHLY, BootCalendarStore.read(context)!!.viewModeSettings.mode)
        assertFalse(BootCalendarStore.read(context)!!.calendarViewSettings.mondayFirst)
        prefs.setTheme(ThemeOption.CUSTOM)
        prefs.setCalendarColumns(4)
        prefs.setHorizontalOffset(-12f)
        prefs.setApplyMode(WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME)
        assertEquals(prefs.settings, BootCalendarStore.read(context))
    }

    @Test fun `disabled rotation and selected Yil survive CE reload and preunlock provider`() {
        val prefs = LifeDotsPreferences(context)
        prefs.setUmrBirthday(epoch(1960, 5, 1))
        prefs.setAutoSwitchEnabled(true)
        prefs.setTopViewMode(TopViewMode.YIL)
        prefs.setAutoSwitchEnabled(false)
        val reopened = LifeDotsPreferences(context).settings
        assertFalse(reopened.autoSwitchSettings.enabled)
        assertEquals(TopViewMode.YIL, reopened.topViewMode)
        unlock(false)
        val beforeUnlock = settingsForRendering(context)!!
        assertEquals(reopened, beforeUnlock)
        assertFalse(beforeUnlock.autoSwitchSettings.enabled)
        assertEquals(TopViewMode.YIL, beforeUnlock.topViewMode)
    }

    @Test fun `unlock uses authoritative CE preferences and never restores stale mirror over them`() {
        val prefs = LifeDotsPreferences.getInstance(context)
        prefs.setTheme(ThemeOption.LIGHT)
        prefs.setCalendarColumns(2)
        val saved = prefs.settings
        val normalBefore = normalStore().all.toMap()
        BootCalendarStore.publish(context, custom())
        unlock(false)
        assertEquals(custom(), settingsForRendering(context))
        unlock(true)
        assertEquals(saved, settingsForRendering(context))
        assertEquals(normalBefore, normalStore().all)
        assertEquals(saved, LifeDotsPreferences(context).settings)
        assertEquals(saved, BootCalendarStore.read(context))
    }

    @Test fun `missing mirror remains absent while locked and cannot initialize normal preferences`() {
        unlock(false)
        assertNull(settingsForRendering(context))
        assertThrows(IllegalStateException::class.java) { LifeDotsPreferences(context) }
        assertThrows(IllegalStateException::class.java) { LifeDotsPreferences.getInstance(context) }
        BootCalendarStore.publish(context, custom())
        assertNull(settingsForRendering(context))
        assertTrue(normalStore().all.isEmpty())
        assertNull(ReflectionHelpers.getStaticField<LifeDotsPreferences?>(LifeDotsPreferences::class.java, "instance"))
    }
}
