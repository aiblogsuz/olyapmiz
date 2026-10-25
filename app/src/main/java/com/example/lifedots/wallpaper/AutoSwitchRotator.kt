package com.example.lifedots.wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.lifedots.preferences.AutoSwitchSettings
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.receiver.DateChangeReceiver

/** Visible-only ticks; hidden engines catch up from wall-clock time when shown. */
class AutoSwitchRotator(
    private val context: Context,
    private val onTick: () -> Unit,
) {
    init { cancelAlarm() } // Retire the global alarm used by older releases.

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { onTick(); reschedule() }
    private var lastSettings: WallpaperSettings? = null
    private var visibleNow: Boolean = false

    /** Call when wallpaper visibility changes. */
    fun setVisible(visible: Boolean) {
        visibleNow = visible
        reschedule()
    }

    /** Call after settings change. */
    fun refresh(settings: WallpaperSettings?) {
        lastSettings = settings
        reschedule()
    }

    fun cancel() {
        handler.removeCallbacks(tickRunnable)
    }

    private fun reschedule() {
        handler.removeCallbacks(tickRunnable)
        val s = lastSettings ?: return
        val auto = s.autoSwitchSettings
        if (!auto.enabled || auto.intervalMs <= 0L) {
            return
        }
        val now = System.currentTimeMillis()
        val msUntilNextBoundary = millisUntilNextBoundary(now, auto) ?: run {
            return
        }

        // Handler — only worth setting while visible.
        if (visibleNow) {
            handler.postDelayed(tickRunnable, msUntilNextBoundary)
        }

    }

    private fun cancelAlarm() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = DateChangeReceiver.ACTION_AUTO_SWITCH_TICK
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags) ?: return
        am.cancel(pi)
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 1042

        internal fun millisUntilNextBoundary(now: Long, auto: AutoSwitchSettings): Long? {
            if (!auto.enabled || auto.intervalMs <= 0L) return null

            val elapsed = now - auto.referenceMs
            if (elapsed < 0L) {
                return (-elapsed + auto.intervalMs).coerceAtLeast(1L)
            }

            val remainder = elapsed % auto.intervalMs
            return (auto.intervalMs - remainder).coerceAtLeast(1L)
        }
    }
}
