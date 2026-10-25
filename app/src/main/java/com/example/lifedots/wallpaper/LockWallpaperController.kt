package com.example.lifedots.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.lifedots.preferences.isUserUnlocked
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.WallpaperApplyMode
import com.example.lifedots.receiver.DateChangeReceiver

/** A saved destination is not evidence that the current wallpaper belongs to us. */
internal fun ownsLockWallpaper(savedId: Int, currentId: Int): Boolean =
    savedId > 0 && currentId == savedId

/** Serializes user application and maintenance; call off the main thread. */
object LockWallpaperController {
    private const val STORE = "lock_wallpaper_ownership"
    private const val LOCK_ID = "lock_id"

    @Synchronized
    fun apply(context: Context) {
        check(isUserUnlocked(context)) { "Unlock your phone before applying a wallpaper." }
        val app = context.applicationContext
        val wm = WallpaperManager.getInstance(app)
        check(wm.isWallpaperSupported && wm.isSetWallpaperAllowed) {
            "Wallpaper changes are not allowed on this device."
        }
        val snapshot = renderSelected(app)
        val black = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        var homeChanged = false
        try {
            // Render first. Changing home first avoids Android copying the old home
            // wallpaper over the freshly applied lock image on some devices.
            check(wm.setBitmap(black, null, true, WallpaperManager.FLAG_SYSTEM) > 0) {
                "Android did not confirm the home wallpaper."
            }
            homeChanged = true
            val id = wm.setBitmap(snapshot, null, true, WallpaperManager.FLAG_LOCK)
            check(id > 0) { "Android did not confirm the lock-screen wallpaper." }
            check(app.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putInt(LOCK_ID, id).commit()) {
                "Wallpaper applied, but automatic refresh could not be saved. Tap Apply again."
            }
            DateChangeReceiver.scheduleDailyAlarm(app)
        } catch (error: Exception) {
            if (homeChanged) throw IllegalStateException(
                "Home wallpaper changed; lock-screen setup could not finish. ${error.message ?: "Please try again."}", error
            )
            throw error
        } finally {
            snapshot.recycle()
            black.recycle()
        }
    }

    @Synchronized
    fun refreshIfOwned(context: Context): Boolean {
        if (!isUserUnlocked(context)) return false
        val app = context.applicationContext
        val preferences = LifeDotsPreferences.getInstance(app)
        if (preferences.settings.applyMode != WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME) return false
        val store = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val savedId = store.getInt(LOCK_ID, -1)
        val wm = WallpaperManager.getInstance(app)
        if (!ownsLockWallpaper(savedId, wm.getWallpaperId(WallpaperManager.FLAG_LOCK))) return false
        val snapshot = renderSelected(app)
        try {
            // External changes can occur while rendering. Recheck at the write boundary.
            if (preferences.settings.applyMode != WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME ||
                !ownsLockWallpaper(savedId, wm.getWallpaperId(WallpaperManager.FLAG_LOCK))) return false
            val id = wm.setBitmap(snapshot, null, true, WallpaperManager.FLAG_LOCK)
            check(id > 0 && store.edit().putInt(LOCK_ID, id).commit()) {
                "Could not save refreshed wallpaper ownership."
            }
            return true
        } finally {
            snapshot.recycle()
        }
    }

    private fun renderSelected(context: Context): Bitmap {
        val settings = LifeDotsPreferences.getInstance(context).settings
        val metrics = context.resources.displayMetrics
        return SnapshotRenderer.render(context, metrics.widthPixels, metrics.heightPixels,
            settings.copy(autoSwitchSettings = settings.autoSwitchSettings.copy(enabled = false)))
    }
}
