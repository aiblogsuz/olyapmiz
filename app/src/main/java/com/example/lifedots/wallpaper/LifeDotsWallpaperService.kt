package com.example.lifedots.wallpaper

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

        private val preferences by lazy { LifeDotsPreferences.getInstance(applicationContext) }
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var lastDrawnDay = -1

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
                if (visible && preferences.settings.animationSettings.enabled) {
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
                if (visible && preferences.settings.fluidEffectSettings.enabled) {
                    fluidPhase += 0.02f * preferences.settings.fluidEffectSettings.flowSpeed
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
            autoSwitchRotator.refresh(preferences.settings)
            handler.post {
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
            autoSwitchRotator.refresh(preferences.settings)
            // Schedule the daily refresh alarm the first time the wallpaper runs, so
            // users don't have to wait for a reboot for the safety net to arm.
            DateChangeReceiver.scheduleDailyAlarm(applicationContext)
            // Anchor our process in the foreground so Samsung Freecess can't freeze
            // it while the wallpaper is hidden behind the lock screen.
            if (!isPreview) {
                activeEngineCount++
                KeepAliveService.start(applicationContext)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            LifeDotsPreferences.removeWallpaperChangeListener(settingsChangeListener)
            autoSwitchRotator.cancel()
            if (!isPreview) {
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
            autoSwitchRotator.setVisible(visible)
            if (visible) {
                draw()
                scheduleNextMidnightCheck()
                // Start animation loop if animations are enabled
                if (preferences.settings.animationSettings.enabled) {
                    animationTime = System.currentTimeMillis()
                    handler.post(animationRunner)
                }
                // Start fluid loop if fluid effects are enabled
                if (preferences.settings.fluidEffectSettings.enabled) {
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
                    renderer.render(canvas, preferences.settings, System.currentTimeMillis(),
                        animationTime, fluidPhase, systemSafeInsetTop, systemSafeInsetBottom,
                        systemSafeInsetLeft, systemSafeInsetRight)
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
