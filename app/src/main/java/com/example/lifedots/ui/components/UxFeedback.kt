package com.example.lifedots.ui.components

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Centralized haptic + sound feedback for interactive elements.
 *
 * Three feedback levels:
 *  - click(): light tick (TextHandleMove) + system CLICK sound — buttons, chips, cards
 *  - confirm(): stronger tick (LongPress) + system CLICK — toggles, save button
 *  - tick(): soft tick only, no sound — slider drag (would be noisy)
 *
 * Both haptic and sound are gated by the user's settings
 * (soundsEnabled / vibrationsEnabled). Sounds also respect the
 * system Touch Sounds setting (playSoundEffect is a no-op if the
 * OS-level Touch Sounds is off). Our toggle is additive on top.
 */
class UxFeedback internal constructor(
    private val haptic: HapticFeedback,
    private val playSound: (effect: Int) -> Unit,
    private val soundsEnabled: () -> Boolean,
    private val vibrationsEnabled: () -> Boolean,
) {
    fun click() {
        if (vibrationsEnabled()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (soundsEnabled()) playSound(SoundEffectConstants.CLICK)
    }

    fun confirm() {
        if (vibrationsEnabled()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (soundsEnabled()) playSound(SoundEffectConstants.CLICK)
    }

    fun tick() {
        if (vibrationsEnabled()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/**
 * Compose helper: returns a UxFeedback instance bound to the current
 * haptic + view + the live values of soundsEnabled / vibrationsEnabled.
 *
 * The boolean params are captured by the lambdas inside `remember`,
 * so a settings change triggers recomposition and a fresh UxFeedback
 * instance with the latest values.
 *
 * Usage:
 *   val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)
 *   Button(onClick = { feedback.click(); doThing() }) { ... }
 */
@Composable
fun rememberUxFeedback(
    soundsEnabled: Boolean,
    vibrationsEnabled: Boolean,
): UxFeedback {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(haptic, view, soundsEnabled, vibrationsEnabled) {
        UxFeedback(
            haptic = haptic,
            playSound = { effect -> view.playSoundEffect(effect) },
            soundsEnabled = { soundsEnabled },
            vibrationsEnabled = { vibrationsEnabled },
        )
    }
}
