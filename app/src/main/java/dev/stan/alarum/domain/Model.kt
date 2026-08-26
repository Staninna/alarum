package dev.stan.alarum.domain

import kotlinx.serialization.Serializable

/** How loud, and with what sound, a stage should be. */
@Serializable
data class AudioSpec(
    val enabled: Boolean = true,
    /** Built-in tone id, or a content:// uri for a user-picked ringtone. */
    val sound: String = Sounds.SOFT_CHIME,
    /** Linear 0..1 at the start of the stage. The effector applies the perceptual curve. */
    val startLevel: Float = 0f,
    val endLevel: Float = 1f,
    /**
     * Pin the system alarm stream to maximum for the duration of this stage.
     * The app's own gain still applies on top, so this is about removing the
     * ceiling rather than being instantly loud.
     */
    val commandeerSystemVolume: Boolean = false,
) {
    fun levelAt(progress: Float): Float =
        startLevel + (endLevel - startLevel) * progress.coerceIn(0f, 1f)
}

object Sounds {
    const val SOFT_CHIME = "builtin:soft_chime"
    const val WARM_PAD = "builtin:warm_pad"
    const val PULSE_TONE = "builtin:pulse_tone"
    const val HARSH_BEEP = "builtin:harsh_beep"
    const val SIREN = "builtin:siren"

    val builtins = listOf(SOFT_CHIME, WARM_PAD, PULSE_TONE, HARSH_BEEP, SIREN)

    fun label(id: String): String = when (id) {
        SOFT_CHIME -> "Soft chime"
        WARM_PAD -> "Warm pad"
        PULSE_TONE -> "Pulse"
        HARSH_BEEP -> "Harsh beep"
        SIREN -> "Siren"
        else -> "Custom sound"
    }
}

@Serializable
enum class VibePattern { NONE, SOFT_PULSE, PULSE, HEARTBEAT, RELENTLESS }

@Serializable
data class HapticSpec(
    val pattern: VibePattern = VibePattern.NONE,
    /** 1..255, ignored on devices without amplitude control. */
    val amplitude: Int = 128,
)

@Serializable
data class FlashSpec(
    /** Drive the screen to this brightness, 0..1. Zero leaves the screen alone. */
    val screenBrightness: Float = 0f,
    /** Torch strobe frequency in Hz. Zero means the torch stays off. */
    val torchHz: Float = 0f,
)

@Serializable
enum class DismissalMethod {
    TAP,
    LONG_PRESS,
    MATH,
    SHAKE,
    NFC;

    val label: String
        get() = when (this) {
            TAP -> "Tap"
            LONG_PRESS -> "Long press"
            MATH -> "Solve maths"
            SHAKE -> "Shake it"
            NFC -> "Scan NFC tag"
        }
}

@Serializable
data class DismissalSpec(
    val method: DismissalMethod = DismissalMethod.TAP,
    /** 1..5. Meaning depends on the method: digits, shake count, hold seconds. */
    val difficulty: Int = 1,
)

/**
 * One step of the ramp. Stages run in order; the last one never ends, it just
 * sustains until dismissed.
 */
@Serializable
data class Stage(
    val id: String,
    val name: String,
    val durationSec: Int,
    val audio: AudioSpec = AudioSpec(),
    val haptics: HapticSpec = HapticSpec(),
    val flash: FlashSpec = FlashSpec(),
    val dismissal: DismissalSpec = DismissalSpec(),
    val allowSnooze: Boolean = true,
    /**
     * Optional escape hatch: an HA script or scene entity to run when this stage
     * begins, for people who would rather not write an automation. The primary
     * mechanism is the published stage state, which HA triggers off.
     */
    val haScript: String? = null,
)

@Serializable
data class EscalationProfile(
    val id: String,
    val name: String,
    val stages: List<Stage>,
) {
    /** Seconds until the final stage begins. The final stage itself is unbounded. */
    val rampSec: Int get() = stages.dropLast(1).sumOf { it.durationSec }
}

@Serializable
data class Alarm(
    val id: String,
    val label: String = "",
    val hour: Int,
    val minute: Int,
    /** ISO day numbers, 1 = Monday. Empty means fire once, at the next occurrence. */
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val profileId: String,
    /** Set when you skip tomorrow without disabling the alarm entirely. */
    val skipNext: Boolean = false,
    val snoozeMinutes: Int = 9,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()
}
