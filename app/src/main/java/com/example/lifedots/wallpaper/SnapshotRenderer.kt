package com.example.lifedots.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.WallpaperSettings

/** Static frame of exactly the same customized calendar used by the live engine. */
object SnapshotRenderer {
    fun render(
        context: Context,
        width: Int,
        height: Int,
        settings: WallpaperSettings = LifeDotsPreferences.getInstance(context).settings,
        now: Long = System.currentTimeMillis(),
    ): Bitmap {
        require(width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            CalendarRenderer().render(Canvas(bitmap), settings, now)
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }
}
