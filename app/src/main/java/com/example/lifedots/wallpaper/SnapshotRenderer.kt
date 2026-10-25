package com.example.lifedots.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.ThemeOption
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.UmrVisualMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.preferences.currentEffectiveMode

/**
 * Renders a STATIC bitmap snapshot of the user's current Yil or Umr state.
 * Used by the "calendar on lock, black on home" apply path — Android won't
 * let a live wallpaper occupy only the lock screen, so we render today's
 * frame to a bitmap and pin it to FLAG_LOCK while a 1x1 black goes to
 * FLAG_SYSTEM.
 *
 * Mirrors the wallpaper engine's draw paths as closely as a still image
 * can: same theme colour palette, same layout maths, same dot/X mode,
 * year-row gradient, parent rings, event markers, and 3-counter band.
 * Animations are dropped because there's no frame loop on a still image.
 */
object SnapshotRenderer {

    fun render(context: Context, width: Int, height: Int): Bitmap {
        val prefs = LifeDotsPreferences.getInstance(context)
        val settings = prefs.settings
        val now = System.currentTimeMillis()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(themeBackground(settings))

        when (currentEffectiveMode(now, settings)) {
            TopViewMode.UMR -> {
                if (settings.umrSettings.birthdayEpochMs != 0L) {
                    drawUmrSnapshot(canvas, settings, width, height, now)
                } else {
                    drawTitleFallback(canvas, settings, width, height)
                }
            }
            TopViewMode.YIL -> drawYilSnapshot(canvas, settings, width, height, now)
        }
        return bitmap
    }

    /**
     * Minimal Yil calendar snapshot. Mirrors the layout math of
     * CalendarLayout / drawCalendarView, but only draws the 12-month dot
     * grid with past / today / future colouring.
     */
    private fun drawYilSnapshot(
        canvas: Canvas,
        settings: WallpaperSettings,
        widthPx: Int,
        heightPx: Int,
        now: Long,
    ) {
        val layout = CalendarLayout.compute(
            widthPx = widthPx,
            heightPx = heightPx,
            topOffsetPx = 0f,
            bottomOffsetPx = 0f,
            systemSafeInsetTopPx = 0,
            systemSafeInsetBottomPx = 0,
            systemSafeInsetLeftPx = 0,
            systemSafeInsetRightPx = 0,
        )

        val cols = settings.calendarViewSettings.columnsPerRow.coerceIn(2, 4)
        val rows = (12 + cols - 1) / cols

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(java.util.Calendar.YEAR)
        val todayMonth = cal.get(java.util.Calendar.MONTH)
        val todayDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val mondayFirst = settings.calendarViewSettings.mondayFirst

        val gapRatio = layout.dotGapRatio
        val marginRatio = layout.monthMarginRatio
        val availWidth = widthPx - 2f * layout.paddingXPx
        val availHeight = (layout.statsBottomBaselinePx - layout.safeTopPx).coerceAtLeast(1f)

        val perRowDotsW = 7 + 6 * gapRatio
        val perColDotsH = 6 + 5 * gapRatio
        val widthDenom = cols * perRowDotsW + (cols - 1) * marginRatio
        val heightDenom = rows * perColDotsH + (rows - 1) * marginRatio
        val dotSize = minOf(
            availWidth / widthDenom,
            availHeight / heightDenom,
            layout.dotSizeCapPx,
        ).coerceAtLeast(1f)
        val dotGap = dotSize * gapRatio
        val monthBoxW = 7 * dotSize + 6 * dotGap
        val monthBoxH = 6 * dotSize + 5 * dotGap
        val monthMargin = dotSize * marginRatio

        val gridLeft = layout.paddingXPx + (availWidth - (cols * monthBoxW + (cols - 1) * monthMargin)) / 2f
        val gridTop = layout.safeTopPx

        // Mirror the live engine: the whole grid responds to the user's
        // Yil position/scale sliders, so the lock-screen snapshot matches
        // what the home screen shows.
        val position = settings.positionSettings
        canvas.save()
        canvas.translate(
            widthPx * (position.horizontalOffset / 100f),
            heightPx * (position.verticalOffset / 100f),
        )
        canvas.scale(position.scale, position.scale)

        val filled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeFilledDot(settings)
            alpha = (settings.filledDotAlpha * 255f).toInt().coerceIn(0, 255)
        }
        val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeEmptyDot(settings)
            alpha = (settings.emptyDotAlpha * 255f).toInt().coerceIn(0, 255)
        }
        val today = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeTodayDot(settings)
            alpha = 255
        }
        val r = dotSize / 2f

        for (monthIdx in 0 until 12) {
            val mRow = monthIdx / cols
            val mCol = monthIdx % cols
            val boxLeft = gridLeft + mCol * (monthBoxW + monthMargin)
            val boxTop = gridTop + mRow * (monthBoxH + monthMargin)

            cal.set(year, monthIdx, 1)
            val firstWeekday = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

            val offset = if (mondayFirst) {
                ((firstWeekday + 5) % 7)
            } else {
                (firstWeekday - 1)
            }

            for (day in 1..daysInMonth) {
                val cellIdx = offset + (day - 1)
                val weekCol = cellIdx % 7
                val weekRow = cellIdx / 7
                if (weekRow >= 6) break

                val cx = boxLeft + weekCol * (dotSize + dotGap) + r
                val cy = boxTop + weekRow * (dotSize + dotGap) + r

                val paint = when {
                    monthIdx == todayMonth && day == todayDay -> today
                    monthIdx < todayMonth || (monthIdx == todayMonth && day < todayDay) -> filled
                    else -> empty
                }
                canvas.drawCircle(cx, cy, r, paint)
            }
        }

        canvas.restore()
    }

    private fun drawUmrSnapshot(
        canvas: Canvas,
        settings: WallpaperSettings,
        widthPx: Int,
        heightPx: Int,
        now: Long,
    ) {
        // Snapshot path has no WindowInsets access; the 22–28% aspect-driven
        // safe-top already reserves the lockscreen clock band, so zeros here
        // are the engine's own lock-screen reality, not an omission.
        val layout = UmrLayoutCompute.compute(
            widthPx = widthPx,
            heightPx = heightPx,
            topOffsetPx = 0f,
            bottomOffsetPx = 0f,
            systemSafeInsetTopPx = 0,
            systemSafeInsetBottomPx = 0,
            systemSafeInsetLeftPx = 0,
            systemSafeInsetRightPx = 0,
        )

        val filledColor = themeFilledDot(settings)
        val emptyColor = themeEmptyDot(settings)
        val isX = settings.umrSettings.visualMode == UmrVisualMode.X_MARKS
        val filledAlpha = (settings.umrSettings.livedAlpha * 255f).toInt().coerceIn(0, 255)
        val emptyAlpha = (settings.umrSettings.emptyAlpha * 255f).toInt().coerceIn(0, 255)

        drawCounterBand(canvas, settings, layout, widthPx, heightPx, now)

        val position = settings.umrSettings.position
        canvas.save()
        canvas.translate(
            widthPx * (position.horizontalOffset / 100f),
            heightPx * (position.verticalOffset / 100f),
        )
        canvas.scale(position.scale, position.scale)

        val birthdayMs = settings.umrSettings.birthdayEpochMs
        val totalCells = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS
        val weeksLived = if (birthdayMs != 0L && now >= birthdayMs) {
            ((now - birthdayMs) / UmrLayoutCompute.MS_PER_WEEK).toInt().coerceAtMost(totalCells - 1)
        } else -1
        if (weeksLived in 0 until totalCells) {
            drawYearRowGradient(canvas, layout, filledColor, weeksLived)
        }

        val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = filledColor; alpha = filledAlpha
        }
        val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isX) 0xFFFFFFFF.toInt() else emptyColor
            alpha = emptyAlpha
        }
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = filledColor; alpha = filledAlpha
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
            strokeWidth = layout.dotSizePx * 0.18f
        }
        val r = layout.dotSizePx / 2f
        for (i in 0 until totalCells) {
            val (cx, cy) = UmrLayoutCompute.cellCenter(layout, i)
            val lived = weeksLived >= 0 && i <= weeksLived
            if (lived) {
                if (isX) {
                    val s = r * 0.85f
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, crossPaint)
                    canvas.drawLine(cx - s, cy + s, cx + s, cy - s, crossPaint)
                } else {
                    canvas.drawCircle(cx, cy, r, filledPaint)
                }
            } else {
                canvas.drawCircle(cx, cy, r, emptyPaint)
            }
        }

        drawParentMarker(
            canvas, layout, settings.umrSettings.momBirthdayEpochMs, now,
            0xFFE53935.toInt(), isX,
        )
        drawParentMarker(
            canvas, layout, settings.umrSettings.dadBirthdayEpochMs, now,
            0xFF2D75A8.toInt(), isX,
        )

        if (settings.eventSettings.enabled) {
            for (event in settings.eventSettings.events) {
                val cell = weekIndexFor(birthdayMs, event.targetDate)
                    .coerceAtMost(totalCells - 1)
                if (cell < 0) continue
                val (cx, cy) = UmrLayoutCompute.cellCenter(layout, cell)
                drawEventMarker(canvas, cx, cy, r, event.color, isX, layout)
            }
        }

        canvas.restore()
    }

    private fun drawYearRowGradient(
        canvas: Canvas,
        layout: UmrLayout,
        filledColor: Int,
        weeksLived: Int,
    ) {
        val yourRow = weeksLived / UmrLayoutCompute.COLS
        val rowTop = layout.gridTopPx + yourRow * (layout.dotSizePx + layout.dotGapPx)
        val rowBottom = rowTop + layout.dotSizePx
        val pad = layout.dotSizePx * 0.75f
        val r = (filledColor shr 16) and 0xFF
        val g = (filledColor shr 8) and 0xFF
        val b = filledColor and 0xFF
        val mid = Color.argb(64, r, g, b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                layout.gridLeftPx, 0f, layout.gridLeftPx + layout.gridWidthPx, 0f,
                intArrayOf(0x00000000, mid, 0x00000000),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(
            layout.gridLeftPx, rowTop - pad,
            layout.gridLeftPx + layout.gridWidthPx, rowBottom + pad,
            paint,
        )
    }

    private fun drawParentMarker(
        canvas: Canvas,
        layout: UmrLayout,
        birthdayMs: Long,
        now: Long,
        color: Int,
        isX: Boolean,
    ) {
        val cell = weekIndexFor(birthdayMs, now).coerceAtMost(
            UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS - 1
        )
        if (cell < 0) return
        val (cx, cy) = UmrLayoutCompute.cellCenter(layout, cell)
        val r = layout.dotSizePx / 2f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            alpha = 150
            maskFilter = BlurMaskFilter(layout.dotSizePx * 1.6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, r * 2.3f, glowPaint)
        if (isX) {
            val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
                this.color = color; alpha = 255
                strokeWidth = layout.dotSizePx * 0.32f
            }
            val s = r * 0.95f
            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, crossPaint)
            canvas.drawLine(cx - s, cy + s, cx + s, cy - s, crossPaint)
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; this.color = color; alpha = 255
            }
            canvas.drawCircle(cx, cy, r, fillPaint)
        }
    }

    private fun drawEventMarker(
        canvas: Canvas,
        cx: Float, cy: Float, r: Float,
        eventColor: Int,
        isX: Boolean,
        layout: UmrLayout,
    ) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = eventColor; alpha = 150
            maskFilter = BlurMaskFilter(layout.dotSizePx * 1.6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, r * 2.3f, glow)
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = eventColor; alpha = 255
        }
        canvas.drawCircle(cx, cy, r, solid)
        if (isX) {
            val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
                color = 0xFFFFFFFF.toInt(); alpha = 235
                strokeWidth = layout.dotSizePx * 0.20f
            }
            val s = r * 0.55f
            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, cross)
            canvas.drawLine(cx - s, cy + s, cx + s, cy - s, cross)
        }
    }

    private fun drawCounterBand(
        canvas: Canvas,
        settings: WallpaperSettings,
        layout: UmrLayout,
        widthPx: Int,
        heightPx: Int,
        now: Long,
    ) {
        val statsShift = heightPx * (settings.umrSettings.statsBandOffset / 100f)
        val bandTop = layout.counterBandTopPx + statsShift
        val bandBottom = layout.gridTopPx + statsShift
        val bandHeight = (bandBottom - bandTop).coerceAtLeast(1f)
        val textSize = bandHeight * 0.30f

        val filledColor = themeFilledDot(settings)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            this.textSize = textSize
            color = filledColor
        }
        val swatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val totalWeeks = settings.umrSettings.totalWeeks
        val you = weekIndexFor(settings.umrSettings.birthdayEpochMs, now).let { if (it < 0) null else it }
        val mom = weekIndexFor(settings.umrSettings.momBirthdayEpochMs, now).let { if (it < 0) null else it }
        val dad = weekIndexFor(settings.umrSettings.dadBirthdayEpochMs, now).let { if (it < 0) null else it }

        val cellWidth = widthPx.toFloat() / 3f
        val swatchRadius = textSize * 0.32f
        val swatchY = bandTop + bandHeight * 0.38f
        val numberY = bandTop + bandHeight * 0.55f
        val labelY = bandTop + bandHeight * 0.88f

        data class Col(val label: String, val weeks: Int?, val color: Int)
        val cols = listOf(
            Col("Me",  you, filledColor),
            Col("Mom", mom, 0xFFE53935.toInt()),
            Col("Dad", dad, 0xFF2D75A8.toInt()),
        )
        cols.forEachIndexed { idx, c ->
            val cx = cellWidth * (idx + 0.5f)
            val swatchX = cellWidth * idx + cellWidth * 0.12f
            val numberText = if (c.weeks == null) "— / $totalWeeks" else "${c.weeks} / $totalWeeks"
            text.alpha = if (c.weeks == null) 100 else 230
            swatch.color = c.color
            swatch.alpha = if (c.weeks == null) 80 else 220
            canvas.drawCircle(swatchX, swatchY, swatchRadius, swatch)
            text.textSize = textSize
            canvas.drawText(numberText, cx, numberY, text)
            text.textSize = textSize * 0.55f
            canvas.drawText(c.label, cx, labelY, text)
        }
    }

    private fun drawTitleFallback(
        canvas: Canvas,
        settings: WallpaperSettings,
        widthPx: Int,
        heightPx: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeFilledDot(settings)
            textAlign = Paint.Align.CENTER
            textSize = widthPx * 0.10f
            isSubpixelText = true
        }
        canvas.drawText("O'lyapmiz", widthPx / 2f, heightPx * 0.55f, paint)
    }

    // ---- Theme palette — EXACTLY mirrors LifeDotsWallpaperService.getThemeColors. ----

    private fun themeBackground(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> Color.parseColor("#F5F5F5")
        ThemeOption.DARK -> Color.parseColor("#1A1A1A")
        ThemeOption.AMOLED -> Color.parseColor("#000000")
        ThemeOption.CUSTOM -> settings.customColors.backgroundColor
    }

    private fun themeFilledDot(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> Color.parseColor("#2C2C2C")
        ThemeOption.DARK -> Color.parseColor("#E0E0E0")
        ThemeOption.AMOLED -> Color.parseColor("#FFFFFF")
        ThemeOption.CUSTOM -> settings.customColors.filledDotColor
    }

    private fun themeEmptyDot(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> Color.parseColor("#D0D0D0")
        ThemeOption.DARK -> Color.parseColor("#3A3A3A")
        ThemeOption.AMOLED -> Color.parseColor("#2A2A2A")
        ThemeOption.CUSTOM -> settings.customColors.emptyDotColor
    }

    private fun themeTodayDot(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> Color.parseColor("#4A90D9")
        ThemeOption.DARK -> Color.parseColor("#5BA0E9")
        ThemeOption.AMOLED -> Color.parseColor("#6AB0F9")
        ThemeOption.CUSTOM -> settings.customColors.todayDotColor
    }
}
