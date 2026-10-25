package com.example.lifedots.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.lifedots.preferences.isUserUnlocked
import com.example.lifedots.preferences.settingsForRendering
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.WindowInsets
import com.example.lifedots.receiver.DateChangeReceiver
import com.example.lifedots.service.KeepAliveService
import com.example.lifedots.preferences.AnimationSettings
import com.example.lifedots.preferences.AnimationType
import com.example.lifedots.preferences.DotEffectSettings
import com.example.lifedots.preferences.DotShape
import com.example.lifedots.preferences.DotSize
import com.example.lifedots.preferences.DotStyle
import com.example.lifedots.preferences.FluidEffectSettings
import com.example.lifedots.preferences.FluidStyle
import com.example.lifedots.preferences.FooterTextSettings
import com.example.lifedots.preferences.GlassEffectSettings
import com.example.lifedots.preferences.GlassStyle
import com.example.lifedots.preferences.GoalPosition
import com.example.lifedots.preferences.GoalSettings
import com.example.lifedots.preferences.GridDensity
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.PositionSettings
import com.example.lifedots.preferences.TextAlignment
import com.example.lifedots.preferences.ThemeOption
import com.example.lifedots.preferences.TreeEffectSettings
import com.example.lifedots.preferences.TreeStyle
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.UmrVisualMode
import com.example.lifedots.preferences.ViewMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.preferences.currentEffectiveMode
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class LifeDotsWallpaperService : WallpaperService() {

    private var activeEngineCount = 0

    override fun onCreateEngine(): Engine {
        return LifeDotsEngine()
    }

    inner class LifeDotsEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var lastDrawnDay = -1
        private var keepAliveStarted = false
        private var unlockReceiverRegistered = false
        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_UNLOCKED) return
                handler.post {
                    if (isUserUnlocked(applicationContext)) {
                        startKeepAliveIfUnlocked()
                        onVisibilityChanged(visible)
                    }
                }
            }
        }

        private val autoSwitchRotator = AutoSwitchRotator(applicationContext) {
            // onTick — just request a redraw; the formula will pick up the new mode.
            if (visible) draw()
        }

        private val renderer = CalendarRenderer()

        // Animation state
        private var animationTime = 0L
        private var lastAnimationFrame = 0L
        private val animationFrameRate = 60 // FPS
        private val animationFrameDelay = 1000L / animationFrameRate

        // Animation loop
        private val animationRunner = object : Runnable {
            override fun run() {
                if (visible && settingsForRendering(applicationContext)?.animationSettings?.enabled == true) {
                    animationTime = System.currentTimeMillis()
                    draw()
                    handler.postDelayed(this, animationFrameDelay)
                }
            }
        }

        // Fluid effect state - for continuous motion
        private var fluidPhase = 0f
        private val fluidRunner = object : Runnable {
            override fun run() {
                val fluid = settingsForRendering(applicationContext)?.fluidEffectSettings
                if (visible && fluid?.enabled == true) {
                    fluidPhase += 0.02f * fluid.flowSpeed
                    if (fluidPhase > 2 * Math.PI) fluidPhase = 0f
                    draw()
                    handler.postDelayed(this, 50)
                }
            }
        }

        private var systemSafeInsetTop = 0
        private var systemSafeInsetBottom = 0
        private var systemSafeInsetLeft = 0
        private var systemSafeInsetRight = 0

        private val settingsChangeListener: () -> Unit = {
            handler.post {
                autoSwitchRotator.refresh(settingsForRendering(applicationContext))
                if (visible) onVisibilityChanged(true)
            }
        }

        private val midnightChecker = object : Runnable {
            override fun run() {
                val currentDay = getCurrentDayOfYear()
                if (currentDay != lastDrawnDay) {
                    draw()
                }
                scheduleNextMidnightCheck()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            LifeDotsPreferences.addWallpaperChangeListener(settingsChangeListener)
            val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                applicationContext.registerReceiver(unlockReceiver, filter)
            }
            unlockReceiverRegistered = true
            autoSwitchRotator.refresh(settingsForRendering(applicationContext))
            // Schedule the daily refresh alarm the first time the wallpaper runs, so
            // users don't have to wait for a reboot for the safety net to arm.
            DateChangeReceiver.scheduleDailyAlarm(applicationContext)
            // Anchor our process in the foreground so Samsung Freecess can't freeze
            // it while the wallpaper is hidden behind the lock screen.
            startKeepAliveIfUnlocked()
        }

        private fun startKeepAliveIfUnlocked() {
            if (!isPreview && !keepAliveStarted && isUserUnlocked(applicationContext)) {
                keepAliveStarted = true
                activeEngineCount++
                KeepAliveService.start(applicationContext)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            LifeDotsPreferences.removeWallpaperChangeListener(settingsChangeListener)
            autoSwitchRotator.cancel()
            if (unlockReceiverRegistered) {
                applicationContext.unregisterReceiver(unlockReceiver)
                unlockReceiverRegistered = false
            }
            if (keepAliveStarted) {
                activeEngineCount = (activeEngineCount - 1).coerceAtLeast(0)
                if (activeEngineCount == 0) KeepAliveService.stop(applicationContext)
            }
            handler.removeCallbacks(animationRunner)
            handler.removeCallbacks(fluidRunner)
            handler.removeCallbacksAndMessages(null)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(animationRunner)
            handler.removeCallbacks(fluidRunner)
            val settings = settingsForRendering(applicationContext)
            autoSwitchRotator.refresh(settings)
            autoSwitchRotator.setVisible(visible)
            if (visible) {
                startKeepAliveIfUnlocked()
                draw()
                scheduleNextMidnightCheck()
                // Start animation loop if animations are enabled
                if (settings?.animationSettings?.enabled == true) {
                    animationTime = System.currentTimeMillis()
                    handler.post(animationRunner)
                }
                // Start fluid loop if fluid effects are enabled
                if (settings?.fluidEffectSettings?.enabled == true) {
                    handler.post(fluidRunner)
                }
            } else {
                handler.removeCallbacks(midnightChecker)
                handler.removeCallbacks(animationRunner)
                handler.removeCallbacks(fluidRunner)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                val cutoutInsets = insets.getInsets(WindowInsets.Type.displayCutout())
                systemSafeInsetTop = maxOf(systemBars.top, cutoutInsets.top)
                systemSafeInsetBottom = maxOf(systemBars.bottom, cutoutInsets.bottom)
                systemSafeInsetLeft = maxOf(systemBars.left, cutoutInsets.left)
                systemSafeInsetRight = maxOf(systemBars.right, cutoutInsets.right)
            } else {
                @Suppress("DEPRECATION")
                systemSafeInsetTop = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                systemSafeInsetBottom = insets.systemWindowInsetBottom
                @Suppress("DEPRECATION")
                systemSafeInsetLeft = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                systemSafeInsetRight = insets.systemWindowInsetRight
            }
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            autoSwitchRotator.setVisible(false)
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacksAndMessages(null)
        }

        private fun scheduleNextMidnightCheck() {
            handler.removeCallbacks(midnightChecker)
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }
            val delay = midnight.timeInMillis - now.timeInMillis
            handler.postDelayed(midnightChecker, delay)
        }

        private fun getCurrentDayOfYear(): Int {
            return Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        }

        private fun getTotalDaysInYear(): Int {
            val calendar = Calendar.getInstance()
            return calendar.getActualMaximum(Calendar.DAY_OF_YEAR)
        }

        private fun draw() {
            if (!visible) return

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val currentDay = getCurrentDayOfYear()
                    if (currentDay != lastDrawnDay && lastDrawnDay != -1) {
                        android.util.Log.i("LifeDots", "Day rolled over: $lastDrawnDay → $currentDay")
                    }
                    val settings = settingsForRendering(applicationContext)
                    if (settings == null) {
                        // No valid pre-unlock snapshot: never invent a different calendar.
                        canvas.drawColor(Color.BLACK)
                    } else {
                        renderer.render(canvas, settings, System.currentTimeMillis(),
                            animationTime, fluidPhase, systemSafeInsetTop, systemSafeInsetBottom,
                            systemSafeInsetLeft, systemSafeInsetRight)
                    }
                    lastDrawnDay = currentDay
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: IllegalArgumentException) {
                        // Surface was destroyed
                    }
                }
            }
        }

    }
}
