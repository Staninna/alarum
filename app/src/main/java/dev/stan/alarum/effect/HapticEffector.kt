package dev.stan.alarum.effect

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.stan.alarum.domain.HapticSpec
import dev.stan.alarum.domain.VibePattern

/** Vibration, on the alarm usage so it survives Do Not Disturb. */
class HapticEffector(context: Context) {

    // VibratorManager only exists from API 31; minSdk is 30, so the old
    // service is still the path on Android 11 -- and a crash here would take
    // down the ring itself.
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var current: VibePattern? = null

    fun apply(spec: HapticSpec) {
        if (current == spec.pattern) return
        current = spec.pattern
        vibrator.cancel()
        if (spec.pattern == VibePattern.NONE || !vibrator.hasVibrator()) return

        val amp = spec.amplitude.coerceIn(1, 255)
        val (timings, amplitudes, repeatAt) = when (spec.pattern) {
            VibePattern.SOFT_PULSE -> Triple(
                longArrayOf(0, 120, 2400), intArrayOf(0, amp / 2, 0), 0,
            )
            VibePattern.PULSE -> Triple(
                longArrayOf(0, 300, 900), intArrayOf(0, amp, 0), 0,
            )
            VibePattern.HEARTBEAT -> Triple(
                longArrayOf(0, 110, 140, 220, 1100), intArrayOf(0, amp, 0, amp, 0), 0,
            )
            VibePattern.RELENTLESS -> Triple(
                longArrayOf(0, 700, 180), intArrayOf(0, amp, 0), 0,
            )
            VibePattern.NONE -> return
        }
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, repeatAt),
                attrs,
            )
        }
    }

    fun stop() {
        current = null
        runCatching { vibrator.cancel() }
    }
}
