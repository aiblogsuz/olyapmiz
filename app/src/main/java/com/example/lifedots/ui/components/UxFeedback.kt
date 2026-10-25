package com.example.lifedots.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * User-interaction feedback channel: haptics + (optionally) a soft system
 * tone. Settings surface three interaction kinds:
 *
 *  - [click]   — taps on buttons/cards/pills
 *  - [tick]    — crossing an anchor while dragging a slider
 *  - [confirm] — committing a value (slider released, item saved)
 *
 * Both channels honour the user's soundsEnabled / vibrationsEnabled
 * toggles; a disabled channel is a silent no-op.
 */
interface UxFeedback {
    fun click()
    fun tick()
    fun confirm()
}

/**
 * Remembers a [UxFeedback] bound to the current view + audio manager.
 * Re-created only when the toggles (or the view) change.
 */
@Composable
fun rememberUxFeedback(soundsEnabled: Boolean, vibrationsEnabled: Boolean): UxFeedback {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(soundsEnabled, vibrationsEnabled, view, context) {
        object : UxFeedback {
            private fun haptic(constant: Int) {
                if (!vibrationsEnabled) return
                runCatching { view.performHapticFeedback(constant) }
            }

            private fun tone(tone: Int, durationMs: Int) {
                if (!soundsEnabled) return
                runCatching {
                    val generator = ToneGenerator(AudioManager.STREAM_SYSTEM, VOLUME)
                    generator.startTone(tone, durationMs)
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching { generator.release() }
                    }, durationMs + RELEASE_DELAY_MS)
                }
            }

            override fun click() {
                haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                tone(ToneGenerator.TONE_PROP_ACK, 30)
            }

            override fun tick() {
                haptic(HapticFeedbackConstants.CLOCK_TICK)
            }

            override fun confirm() {
                haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                tone(ToneGenerator.TONE_PROP_BEEP, 60)
            }
        }
    }
}

private const val VOLUME = 40
private const val RELEASE_DELAY_MS = 80L
