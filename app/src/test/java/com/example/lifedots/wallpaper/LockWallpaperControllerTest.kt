package com.example.lifedots.wallpaper

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.WallpaperApplyMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowWallpaperManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [LockWallpaperControllerTest.RecordingWallpaperManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockWallpaperControllerTest {
    private lateinit var context: Application
    private lateinit var wm: WallpaperManager
    private lateinit var prefs: LifeDotsPreferences

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        ReflectionHelpers.setStaticField(LifeDotsPreferences::class.java, "instance", null)
        context.getSharedPreferences("lifedots_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("lock_wallpaper_ownership", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowWallpaperManager.reset()
        RecordingWallpaperManager.writes.clear()
        RecordingWallpaperManager.failLock = false
        RecordingWallpaperManager.lockReadsBeforeReplacement = -1
        context.resources.displayMetrics.apply { widthPixels = 360; heightPixels = 800 }
        wm = WallpaperManager.getInstance(context)
        prefs = LifeDotsPreferences.getInstance(context)
        prefs.setApplyMode(WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME)
    }

    private fun image(color: Int) = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun savedId() = context.getSharedPreferences("lock_wallpaper_ownership", Context.MODE_PRIVATE)
        .getInt("lock_id", -1)

    @Test fun `saved destination alone never overwrites unrelated wallpapers`() {
        wm.setBitmap(image(Color.RED), null, true, WallpaperManager.FLAG_SYSTEM)
        wm.setBitmap(image(Color.BLUE), null, true, WallpaperManager.FLAG_LOCK)
        val homeId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val lockId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val calls = RecordingWallpaperManager.writes.toList()

        assertFalse(LockWallpaperController.refreshIfOwned(context))

        assertEquals(homeId, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        assertEquals(lockId, wm.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertEquals(calls, RecordingWallpaperManager.writes)
        assertEquals(-1, savedId())
    }

    @Test fun `external lock replacement revokes automatic refresh`() {
        LockWallpaperController.apply(context)
        assertEquals(wm.getWallpaperId(WallpaperManager.FLAG_LOCK), savedId())
        wm.setBitmap(image(Color.GREEN), null, true, WallpaperManager.FLAG_LOCK)
        val replacementId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val calls = RecordingWallpaperManager.writes.toList()

        assertFalse(LockWallpaperController.refreshIfOwned(context))

        assertEquals(replacementId, wm.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertEquals(calls, RecordingWallpaperManager.writes)
        assertEquals(Color.GREEN, shadowOf(wm).getBitmap(WallpaperManager.FLAG_LOCK)!!.getPixel(0, 0))
    }

    @Test fun `owned refresh touches only lock and retains ownership for another refresh`() {
        LockWallpaperController.apply(context)
        wm.setBitmap(image(Color.MAGENTA), null, true, WallpaperManager.FLAG_SYSTEM)
        val homeId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val home = shadowOf(wm).getBitmap(WallpaperManager.FLAG_SYSTEM)
        val oldLockId = savedId()
        RecordingWallpaperManager.writes.clear()

        assertTrue(LockWallpaperController.refreshIfOwned(context))
        assertTrue(savedId() > oldLockId)
        assertEquals(wm.getWallpaperId(WallpaperManager.FLAG_LOCK), savedId())
        assertEquals(homeId, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        assertSame(home, shadowOf(wm).getBitmap(WallpaperManager.FLAG_SYSTEM))
        assertEquals(listOf(WallpaperManager.FLAG_LOCK), RecordingWallpaperManager.writes)
        assertTrue(LockWallpaperController.refreshIfOwned(context))
        assertEquals(listOf(WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_LOCK), RecordingWallpaperManager.writes)
    }

    @Test fun `switching back to live mode prevents static refresh`() {
        LockWallpaperController.apply(context)
        prefs.setApplyMode(WallpaperApplyMode.BOTH_LIVE)
        val calls = RecordingWallpaperManager.writes.toList()

        assertFalse(LockWallpaperController.refreshIfOwned(context))
        assertEquals(calls, RecordingWallpaperManager.writes)
    }

    @Test fun `external replacement during rendering is checked before writing`() {
        LockWallpaperController.apply(context)
        RecordingWallpaperManager.writes.clear()
        // The first ownership read succeeds; the write-boundary read sees another app's change.
        RecordingWallpaperManager.lockReadsBeforeReplacement = 1

        assertFalse(LockWallpaperController.refreshIfOwned(context))

        assertTrue(RecordingWallpaperManager.writes.isEmpty())
        assertNotEquals(savedId(), wm.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertEquals(Color.CYAN, shadowOf(wm).getBitmap(WallpaperManager.FLAG_LOCK)!!.getPixel(0, 0))
    }

    @Test fun `lock failure reports changed home and never grants refresh ownership`() {
        wm.setBitmap(image(Color.BLUE), null, true, WallpaperManager.FLAG_LOCK)
        val originalLockId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
        RecordingWallpaperManager.failLock = true

        val failure = assertThrows(IllegalStateException::class.java) { LockWallpaperController.apply(context) }

        assertTrue(failure.message.orEmpty().contains("Home wallpaper changed"))
        assertEquals(originalLockId, wm.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertEquals(Color.BLACK, shadowOf(wm).getBitmap(WallpaperManager.FLAG_SYSTEM)!!.getPixel(0, 0))
        assertEquals(-1, savedId())
        RecordingWallpaperManager.failLock = false
        assertFalse(LockWallpaperController.refreshIfOwned(context))
    }

    /**
     * The stock 4.16 shadow returns constant 1 from setBitmap although its IDs increment.
     * Match Android's ID-return contract and copy input images because production may recycle them.
     */
    @Implements(WallpaperManager::class)
    class RecordingWallpaperManager : ShadowWallpaperManager() {
        companion object {
            val writes = mutableListOf<Int>()
            var failLock = false
            var lockReadsBeforeReplacement = -1
        }

        @Implementation(minSdk = 24)
        protected override fun setBitmap(fullImage: Bitmap, visibleCropHint: Rect?, allowBackup: Boolean, which: Int): Int {
            if (which == WallpaperManager.FLAG_LOCK && failLock) error("Injected lock failure")
            writes += which
            val copy = fullImage.copy(Bitmap.Config.ARGB_8888, false)
            super.setBitmap(copy, visibleCropHint, allowBackup, which)
            return super.getWallpaperId(which)
        }

        @Implementation(minSdk = 24)
        protected override fun getWallpaperId(which: Int): Int {
            if (which == WallpaperManager.FLAG_LOCK && lockReadsBeforeReplacement >= 0) {
                if (lockReadsBeforeReplacement-- == 0) {
                    val external = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
                    super.setBitmap(external, null, true, which)
                }
            }
            return super.getWallpaperId(which)
        }
    }
}
