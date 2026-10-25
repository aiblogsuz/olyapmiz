package com.example.lifedots.receiver

import android.app.AlarmManager
import android.app.Application
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.wallpaper.LifeDotsWallpaperService
import com.example.lifedots.wallpaper.LockWallpaperController
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectBootReceiverTest {
    private lateinit var context: Application
    private lateinit var normalPrefs: SharedPreferences
    private lateinit var ownership: SharedPreferences

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        normalPrefs = context.getSharedPreferences("lifedots_prefs", Context.MODE_PRIVATE)
        ownership = context.getSharedPreferences("lock_wallpaper_ownership", Context.MODE_PRIVATE)
        ownership.edit().clear().commit()
        ReflectionHelpers.setStaticField(LifeDotsPreferences::class.java, "instance", null)
        context.getSharedPreferences("lifedots_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
    }

    @Test fun `locked boot and date broadcasts schedule alarms without CE settings foreground service or activity`() {
        val deviceContext = context.createDeviceProtectedStorageContext()
        // Robolectric keeps each AlarmManager client's scheduled alarms in its own shadow.
        // Retain the actual DP context used by the receiver so assertions observe that client,
        // rather than the application's separate CE AlarmManager client.
        val receiverContext = object : ContextWrapper(context) {
            override fun createDeviceProtectedStorageContext(): Context = deviceContext
        }
        val deviceAlarms = shadowOf(deviceContext.getSystemService(AlarmManager::class.java))
        for (action in listOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, DateChangeReceiver.ACTION_DAILY_TICK,
            DateChangeReceiver.ACTION_AUTO_SWITCH_TICK)) {
            DateChangeReceiver().onReceive(receiverContext, Intent(action))
        }
        assertNull(ReflectionHelpers.getStaticField<LifeDotsPreferences?>(LifeDotsPreferences::class.java, "instance"))
        assertTrue(normalPrefs.all.isEmpty())
        assertNull(shadowOf(context).nextStartedService)
        assertNull(shadowOf(context).nextStartedActivity)
        val alarm = deviceAlarms.scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        assertEquals(AlarmManager.INTERVAL_DAY, alarm.intervalMs)
        assertTrue(alarm.triggerAtMs > System.currentTimeMillis())
        assertEquals(DateChangeReceiver.ACTION_DAILY_TICK, shadowOf(alarm.operation!!).savedIntent.action)
    }

    @Test fun `static controller cannot access credential preferences while locked`() {
        assertFalse(LockWallpaperController.refreshIfOwned(context))
        assertThrows(IllegalStateException::class.java) { LockWallpaperController.apply(context) }
        assertNull(ReflectionHelpers.getStaticField<LifeDotsPreferences?>(LifeDotsPreferences::class.java, "instance"))
        assertTrue(ownership.all.isEmpty())
    }

    @Test fun `only the restored wallpaper and safe date receiver are direct boot aware`() {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        assertTrue(pm.getServiceInfo(ComponentName(context, LifeDotsWallpaperService::class.java), flags).directBootAware)
        assertTrue(pm.getReceiverInfo(ComponentName(context, DateChangeReceiver::class.java), flags).directBootAware)
    }
}
