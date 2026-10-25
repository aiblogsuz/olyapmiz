package com.example.lifedots.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lifedots.ui.theme.BrandColors
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * Continuous slider mapped logarithmically from 1 second to 1 hour.
 *
 * The user drags a slider in [0, 1]; the displayed/saved interval is
 * intervalMs = 1000 * 3600^slider01 (slider=0 -> 1s, slider=1 -> 1h).
 *
 * Resolution snap on the way out:
 *   under 1 minute: nearest 1 second
 *   1 minute to 10 minutes: nearest 5 seconds
 *   above 10 minutes: nearest 1 minute
 *
 * Tick anchors (visual + haptic crossings) at 5s, 30s, 5m, 30m, 1h.
 */
@Composable
fun IntervalSlider(
    currentMs: Long,
    onIntervalChange: (ms: Long) -> Unit,
    feedback: UxFeedback,
    modifier: Modifier = Modifier,
) {
    var slider01 by remember(currentMs) { mutableFloatStateOf(IntervalSliderMath.msToSlider01(currentMs)) }
    val displayedMs = remember(slider01) { IntervalSliderMath.slider01ToSnappedMs(slider01) }
    val label = remember(displayedMs) { IntervalSliderMath.formatLabel(displayedMs) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.AmberGold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = slider01,
            onValueChange = { newValue ->
                if (IntervalSliderMath.crossedTick(slider01, newValue)) feedback.tick()
                slider01 = newValue
            },
            onValueChangeFinished = {
                feedback.confirm()
                onIntervalChange(displayedMs)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Auto-switch interval"
                stateDescription = label
            },
            colors = SliderDefaults.colors(
                thumbColor = BrandColors.AmberGold,
                activeTrackColor = BrandColors.AmberGold,
                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                activeTickColor = BrandColors.InkBlack,
                inactiveTickColor = BrandColors.GoldenMuted,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("1s", "5s", "30s", "5m", "30m", "1h").forEach { tickLabel ->
                Text(
                    text = tickLabel,
                    style = MaterialTheme.typography.bodySmall.copy(color = BrandColors.GoldenMuted),
                )
            }
        }
    }
}

/**
 * Pure-function math for the IntervalSlider. Separated so it's
 * testable without Compose.
 */
object IntervalSliderMath {
    const val MIN_MS = 1_000L
    const val MAX_MS = 3_600_000L

    /** Tick anchor positions on the [0,1] slider (must match the labels in IntervalSlider). */
    val TICK_POSITIONS: List<Float> = listOf(
        0f,                            // 1s
        msToSlider01(5_000L),          // 5s
        msToSlider01(30_000L),         // 30s
        msToSlider01(300_000L),        // 5m
        msToSlider01(1_800_000L),      // 30m
        1f,                            // 1h
    )

    /** Map an interval in ms to a slider position in [0, 1]. */
    fun msToSlider01(ms: Long): Float {
        val clamped = ms.coerceIn(MIN_MS, MAX_MS).toDouble()
        val logMin = ln(MIN_MS.toDouble())
        val logMax = ln(MAX_MS.toDouble())
        return ((ln(clamped) - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
    }

    /** Map a slider position in [0, 1] to an interval in ms (continuous, not snapped). */
    fun slider01ToMs(slider01: Float): Long {
        val logMin = ln(MIN_MS.toDouble())
        val logMax = ln(MAX_MS.toDouble())
        return Math.exp(logMin + slider01.coerceIn(0f, 1f) * (logMax - logMin)).roundToLong()
    }

    /** Map a slider position to a snapped interval. */
    fun slider01ToSnappedMs(slider01: Float): Long {
        val raw = slider01ToMs(slider01)
        return when {
            raw < 60_000L -> ((raw + 500L) / 1_000L) * 1_000L
            raw < 600_000L -> ((raw + 2_500L) / 5_000L) * 5_000L
            else -> ((raw + 30_000L) / 60_000L) * 60_000L
        }.coerceIn(MIN_MS, MAX_MS)
    }

    /** Returns the index of the closest tick anchor if slider01 is within tolerance of it; else -1. */
    fun tickIndexAt(slider01: Float, tolerance: Float = 0.015f): Int {
        var bestIdx = -1
        var bestDist = tolerance
        TICK_POSITIONS.forEachIndexed { idx, pos ->
            val d = kotlin.math.abs(pos - slider01)
            if (d <= bestDist) {
                bestDist = d
                bestIdx = idx
            }
        }
        return bestIdx
    }

    /** Detects anchor crossings even when a fast drag skips its proximity window. */
    fun crossedTick(from: Float, to: Float): Boolean = TICK_POSITIONS.any { anchor ->
        if (to > from) anchor > from && anchor <= to else anchor < from && anchor >= to
    }

    /** Human label for a snapped interval. */
    fun formatLabel(ms: Long): String {
        if (ms >= 3_600_000L) return "Every 1 hour"
        if (ms >= 60_000L) {
            val minutes = ms / 60_000L
            val seconds = (ms % 60_000L) / 1_000L
            if (seconds > 0) return "Every ${minutes}m ${seconds}s"
            return if (minutes == 1L) "Every 1 minute" else "Every $minutes minutes"
        }
        val seconds = ms / 1_000L
        return if (seconds == 1L) "Every 1 second" else "Every $seconds seconds"
    }
}
