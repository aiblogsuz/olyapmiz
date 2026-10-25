package com.example.lifedots.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.lifedots.preferences.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarRendererTest {
    private val now = 1792886400000L
    private fun settings(mode: TopViewMode) = WallpaperSettings(
        topViewMode = mode,
        theme = ThemeOption.CUSTOM,
        positionSettings = PositionSettings(4f, 6f, 0.85f),
        umrSettings = UmrSettings(birthdayEpochMs = 946684800000L,
            position = PositionSettings(-3f, 4f, 0.8f)),
        goalSettings = GoalSettings(goals = listOf(Goal("test", "Milestone", now + 86400000L))),
    )

    @Test fun `shared live and snapshot paths preserve custom calendar for every layout`() {
        val context = RuntimeEnvironment.getApplication()
        for (mode in TopViewMode.entries) for (view in ViewMode.entries) {
            val selected = settings(mode).copy(viewModeSettings = ViewModeSettings(mode = view))
            val live = Bitmap.createBitmap(540, 1200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(live)
            val count = canvas.saveCount
            CalendarRenderer().render(canvas, selected, now)
            assertEquals(count, canvas.saveCount)
            val snapshot = SnapshotRenderer.render(context, 540, 1200, selected, now)
            assertTrue("$mode/$view differs", live.sameAs(snapshot))
            val repeated = SnapshotRenderer.render(context, 540, 1200, selected, now)
            assertTrue(snapshot.sameAs(repeated))
            val output = File("build/reports/calendar-previews/${mode.name}-${view.name}.png")
            output.parentFile.mkdirs()
            output.outputStream().use { snapshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            live.recycle(); snapshot.recycle(); repeated.recycle()
        }
    }

    @Test fun `customization changes actual snapshot pixels`() {
        val context = RuntimeEnvironment.getApplication()
        val base = settings(TopViewMode.YIL)
        val customized = base.copy(positionSettings = PositionSettings(-10f, 12f, 0.7f),
            calendarViewSettings = base.calendarViewSettings.copy(columnsPerRow = 4, mondayFirst = false),
            viewModeSettings = base.viewModeSettings.copy(showMonthLabels = false))
        val before = SnapshotRenderer.render(context, 540, 1200, base, now)
        val after = SnapshotRenderer.render(context, 540, 1200, customized, now)
        assertFalse(before.sameAs(after))
        before.recycle(); after.recycle()
    }
}
