package dev.stan.alarum.effect

import dev.stan.alarum.domain.Sounds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * The built-in alarm tones, as functions of time rather than audio files.
 *
 * Synthesising them means the ramp is genuinely continuous — the same waveform
 * simply gets louder — instead of crossfading between recordings, and it keeps
 * the APK free of binary assets.
 *
 * Each function takes seconds since the tone started and returns a sample in
 * -1..1.
 */
object ToneSynth {

    fun voice(soundId: String): (Double) -> Double = when (soundId) {
        Sounds.SOFT_CHIME -> ::softChime
        Sounds.WARM_PAD -> ::warmPad
        Sounds.PULSE_TONE -> ::pulseTone
        Sounds.HARSH_BEEP -> ::harshBeep
        Sounds.SIREN -> ::siren
        else -> ::pulseTone
    }

    /** A struck bell every four seconds, with a long decay. Barely there. */
    private fun softChime(t: Double): Double {
        val period = 4.0
        val phase = t % period
        val env = exp(-phase * 1.6)
        if (env < 0.001) return 0.0
        val f = 528.0
        return env * (
            0.6 * sin(2 * PI * f * t) +
                0.25 * sin(2 * PI * f * 2.01 * t) +
                0.15 * sin(2 * PI * f * 3.02 * t)
            )
    }

    /** Two slightly detuned voices, always on, with a slow breathing swell. */
    private fun warmPad(t: Double): Double {
        val swell = 0.75 + 0.25 * sin(2 * PI * t / 7.0)
        return swell * (
            0.5 * sin(2 * PI * 220.0 * t) +
                0.3 * sin(2 * PI * 331.0 * t) +
                0.2 * sin(2 * PI * 440.5 * t)
            )
    }

    /** Short insistent beeps. The point where it stops being pleasant. */
    private fun pulseTone(t: Double): Double {
        val period = 0.5
        val phase = t % period
        if (phase > 0.22) return 0.0
        val env = if (phase < 0.01) phase / 0.01 else exp(-(phase - 0.01) * 6.0)
        return env * (0.7 * sin(2 * PI * 880.0 * t) + 0.3 * sin(2 * PI * 1320.0 * t))
    }

    /** A squarish blat. Deliberately unmusical. */
    private fun harshBeep(t: Double): Double {
        val period = 0.28
        val phase = t % period
        if (phase > 0.15) return 0.0
        val s = sin(2 * PI * 1000.0 * t)
        val sq = if (s >= 0) 1.0 else -1.0
        return 0.85 * sq * (0.6 + 0.4 * sin(2 * PI * 7.0 * t))
    }

    /** Rising and falling sweep. The one you cannot sleep through. */
    private fun siren(t: Double): Double {
        val sweep = t % 1.4 / 1.4
        val tri = 1.0 - abs(2.0 * sweep - 1.0)
        val f = 620.0 + 900.0 * tri
        val s = sin(2 * PI * f * t)
        val sq = if (s >= 0) 1.0 else -1.0
        return 0.7 * s + 0.3 * sq
    }
}
