package com.example.lifedots.preferences

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedCalendarTest {
    @Test fun `old migrations preserve custom layout position and week start`() {
        val context = RuntimeEnvironment.getApplication()
        val store = context.getSharedPreferences("lifedots_prefs", Context.MODE_PRIVATE)
        store.edit().clear().putString("view_mode", "MONTHLY")
            .putFloat("vertical_offset", -12f).putBoolean("calendar_monday_first", false)
            .putBoolean("show_month_labels", false).commit()
        val first = LifeDotsPreferences(context).settings
        val reopened = LifeDotsPreferences(context).settings
        assertEquals(ViewMode.MONTHLY, first.viewModeSettings.mode)
        assertEquals(-12f, first.positionSettings.verticalOffset, 0f)
        assertFalse(first.calendarViewSettings.mondayFirst)
        assertFalse(first.viewModeSettings.showMonthLabels)
        assertEquals(first, reopened)
    }

    @Test fun `explicit calendar selection wins immediately and survives reload during rotation`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("lifedots_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = LifeDotsPreferences(context)
        prefs.setUmrBirthday(1L)
        prefs.setAutoSwitchEnabled(true)
        prefs.setTopViewMode(TopViewMode.UMR)
        val saved = prefs.settings
        assertEquals(TopViewMode.UMR, currentEffectiveMode(saved.autoSwitchSettings.referenceMs, saved))
        val restored = LifeDotsPreferences(context).settings
        assertEquals(saved, restored)
        assertEquals(TopViewMode.UMR, currentEffectiveMode(restored.autoSwitchSettings.referenceMs, restored))
        assertEquals(TopViewMode.YIL, currentEffectiveMode(
            restored.autoSwitchSettings.referenceMs + restored.autoSwitchSettings.intervalMs, restored))
    }
}
