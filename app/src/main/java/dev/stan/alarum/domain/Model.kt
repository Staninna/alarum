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

/**
 * What the house should say, and how often it should say it again.
 *
 * The phone never speaks. It publishes the line and Home Assistant decides
 * which speaker says it, in whose voice, and at what volume — the same division
 * as everything else here, and the reason this carries no entity id.
 *
 * A list rather than one line because the point is not to be informative, it
 * is to be impossible to lie next to. [lineAt] is pure so the cycling can be
 * unit tested instead of discovered at 07:00.
 */
@Serializable
data class SpeechSpec(
    val enabled: Boolean = false,
    val lines: List<String> = emptyList(),
    /** Seconds between one line being published and the next. */
    val everySec: Int = 30,
    /** Shuffled, but exhaustively: every line is heard before any repeats. */
    val shuffle: Boolean = false,
) {
    val usableLines: List<String> get() = lines.filter { it.isNotBlank() }

    val active: Boolean get() = enabled && usableLines.isNotEmpty()

    /**
     * The [index]th thing to say, or null if there is nothing to say.
     *
     * [seed] keeps a shuffled order stable across a configuration change, so
     * the ordering does not reset every time the screen rotates.
     */
    fun lineAt(index: Int, seed: Long): String? {
        val usable = usableLines
        if (usable.isEmpty()) return null
        val i = index.mod(usable.size)
        if (!shuffle || usable.size == 1) return usable[i]
        // A fresh permutation per pass through the list, rather than an
        // independent random pick each time, which would repeat and skip.
        val pass = index.floorDiv(usable.size)
        val order = usable.indices.shuffled(kotlin.random.Random(seed + pass * 31L))
        return usable[order[i]]
    }
}

@Serializable
data class FlashSpec(
    /** Drive the screen to this brightness, 0..1. Zero leaves the screen alone. */
    val screenBrightness: Float = 0f,
    /** Torch strobe frequency in Hz. Zero means the torch stays off. */
    val torchHz: Float = 0f,
)

@Serializable
enum class DismissalMethod {
    /**
     * Retired. One tap kills the whole ramp, and a hand that has been awake for
     * four seconds can manage one tap — which made every stage after the first
     * one theoretical.
     *
     * Kept in the enum rather than deleted because profiles.json still contains
     * the name, and an unknown enum value fails the whole parse. JsonStore
     * swallows that and hands back the defaults, so deleting this would quietly
     * replace every profile someone had made. [DismissalSpec.hardened] moves
     * them off it instead.
     */
    TAP,

    /** Retired, for the same reason. Holding a button is not being awake. */
    LONG_PRESS,

    MATH,
    SHAKE,
    NFC;

    /** False for the retired ones: they still parse, they are just not offered. */
    val selectable: Boolean get() = this != TAP && this != LONG_PRESS

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
    val method: DismissalMethod = DismissalMethod.MATH,
    /**
     * [MIN_DIFFICULTY]..5. Meaning depends on the method: how many sums, how
     * many shakes.
     *
     * The floor is not 1. One two-digit sum is something you can do without
     * ever really surfacing, and a dismissal you can complete while asleep is
     * not a dismissal, it is a snooze button with extra steps.
     */
    val difficulty: Int = MIN_DIFFICULTY,
) {
    /**
     * The nearest equivalent that requires actually being awake.
     *
     * Applied to everything on load, so a profile saved before the easy methods
     * and the easy difficulties were retired stops being dismissible in one
     * thumb movement. Pure, so the mapping is a test rather than a surprise at
     * 07:00.
     */
    fun hardened(): DismissalSpec {
        val method = when (method) {
            DismissalMethod.TAP, DismissalMethod.LONG_PRESS -> DismissalMethod.MATH
            else -> method
        }
        return DismissalSpec(method, difficulty.coerceIn(MIN_DIFFICULTY, 5))
    }

    companion object {
        /** Below this it is a formality rather than an obstacle. */
        const val MIN_DIFFICULTY = 3
    }
}

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
    val speech: SpeechSpec = SpeechSpec(),
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
