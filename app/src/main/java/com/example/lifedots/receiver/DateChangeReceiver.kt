package com.example.lifedots.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Color
import com.example.lifedots.preferences.isUserUnlocked
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.WallpaperApplyMode
import com.example.lifedots.service.KeepAliveService
import com.example.lifedots.wallpaper.LockWallpaperController
import java.util.concurrent.Executors
import java.util.Calendar

/**
 * Forces the wallpaper to redraw on:
 *  - System date/time changes (manifest-registered).
 *  - A daily AlarmManager tick at ~00:00:30, as a safety net for devices where
 *    ACTION_DATE_CHANGED is dropped by aggressive battery optimization.
 *  - BOOT_COMPLETED / MY_PACKAGE_REPLACED, so alarms are rescheduled after
 *    every reboot and in-app update.
 *
 * The wallpaper engine itself also redraws on every visibility change, so the
 * worst-case experience is "wallpaper updates the moment you wake the phone".
 */
class DateChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isUserUnlocked(context)) {
            // Direct Boot may access only device-protected storage and safe system services.
            // The OS restores the selected live wallpaper; no CE settings or foreground service here.
            if (intent.action in setOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.QUICKBOOT_POWERON", ACTION_DAILY_TICK,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) {
                scheduleDailyAlarm(context.createDeviceProtectedStorageContext())
            }
            return
        }
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.i("LifeDots", "${intent.action} — scheduling daily refresh alarm")
                scheduleDailyAlarm(context)
                pokeAutoSwitchRedraw(context)
                maybeStartKeepAlive(context)
                refreshLockSnapshotIfNeeded(context)
                // Note: auto-switch alarm is intentionally NOT re-armed here. The engine's
                // process isn't necessarily running at BOOT_COMPLETED time, so the listener
                // chain wouldn't reach the rotator. The rotator arms itself on the first
                // onVisibilityChanged(true) after the user wakes the phone, which is when
                // the mode actually matters.
            }
            ACTION_DAILY_TICK,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                Log.i("LifeDots", "Date/time changed (${intent.action}) — forcing wallpaper redraw")
                pokeAutoSwitchRedraw(context)
                refreshLockSnapshotIfNeeded(context)
                scheduleDailyAlarm(context)
            }
            ACTION_AUTO_SWITCH_TICK -> {
                Log.i("LifeDots", "Auto-switch tick — forcing wallpaper redraw")
                pokeAutoSwitchRedraw(context)
            }
        }
    }

    private fun maybeStartKeepAlive(context: Context) {
        if (!isOwnLiveWallpaperActive(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isIgnoringBatteryOptimizations(context)) {
            Log.i("LifeDots", "KeepAliveService not started from background; battery exemption is not granted")
            return
        }
        KeepAliveService.start(context)
    }

    private fun isOwnLiveWallpaperActive(context: Context): Boolean = try {
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
    } catch (e: Exception) {
        false
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (e: Exception) {
        false
    }

    /**
     * Force a wallpaper redraw by directly notifying listeners — no disk write.
     */
    private fun pokeAutoSwitchRedraw(context: Context) {
        try {
            val prefs = LifeDotsPreferences.getInstance(context)
            prefs.notifyWallpaperChanged()
        } catch (e: Exception) {
            Log.e("LifeDots", "Failed to notify wallpaper change", e)
        }
    }

    /**
     * For users in CALENDAR_LOCK_BLACK_HOME mode, re-render today's snapshot
     * and push it to FLAG_LOCK so the calendar reflects the new date. Skipped
     * silently if the user is in BOTH_LIVE mode (the engine handles its own
     * redraw on the next visibility change).
     *
     * Only fires if we previously set a lock-screen bitmap — otherwise we'd
     * be silently pushing a wallpaper the user never asked for.
     */
    private fun refreshLockSnapshotIfNeeded(context: Context) {
        val pending = goAsync()
        refreshExecutor.execute {
            try {
                LockWallpaperController.refreshIfOwned(context.applicationContext)
            } catch (error: Exception) {
                Log.w("LifeDots", "Daily lock snapshot refresh failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val refreshExecutor = Executors.newSingleThreadExecutor()
        const val ACTION_DAILY_TICK = "com.example.lifedots.DAILY_TICK"
        const val ACTION_AUTO_SWITCH_TICK = "com.example.lifedots.AUTO_SWITCH_TICK"

        fun scheduleDailyAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val intent = Intent(context, DateChangeReceiver::class.java).apply {
                action = ACTION_DAILY_TICK
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pending = PendingIntent.getBroadcast(context, 0, intent, flags)

            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 30)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            try {
                // Inexact repeating — doze-friendly and no permission needed on Android 12+.
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    nextMidnight,
                    AlarmManager.INTERVAL_DAY,
                    pending
                )
            } catch (e: Exception) {
                Log.w("LifeDots", "Failed to schedule daily refresh alarm", e)
            }
        }
    }
}
