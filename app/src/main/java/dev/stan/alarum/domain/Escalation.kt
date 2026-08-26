package dev.stan.alarum.domain

/**
 * What the world should look like at a given moment of a ring.
 *
 * Produced by [EscalationEngine] and consumed by the effectors. Everything here
 * is derived purely from elapsed time and the profile, which is what makes the
 * whole ramp testable without a device, a speaker, or a 7am wake-up.
 */
data class EscalationState(
    val stageIndex: Int,
    val stage: Stage,
    val elapsedSec: Int,
    val elapsedInStageSec: Int,
    /** 0..1 through the current stage. The final stage sits at 1 once it is reached. */
    val stageProgress: Float,
    /** Linear 0..1, already interpolated across the stage. */
    val audioLevel: Float,
    val isFinalStage: Boolean,
)

/**
 * Turns elapsed seconds into an [EscalationState].
 *
 * Stages run back to back in list order. The final stage is unbounded: once the
 * ramp reaches it, it sustains at full intensity until the alarm is dismissed.
 * A profile with no stages is not representable here — construction fails loudly
 * rather than producing a silent alarm.
 */
class EscalationEngine(val profile: EscalationProfile) {

    init {
        require(profile.stages.isNotEmpty()) {
            "Escalation profile '${profile.name}' has no stages; an alarm needs at least one."
        }
    }

    /** Cumulative start second of each stage. */
    private val starts: IntArray = IntArray(profile.stages.size).also { arr ->
        var acc = 0
        profile.stages.forEachIndexed { i, s ->
            arr[i] = acc
            acc += s.durationSec.coerceAtLeast(0)
        }
    }

    val stageCount: Int get() = profile.stages.size

    fun stageStartSec(index: Int): Int = starts[index.coerceIn(0, starts.lastIndex)]

    fun stateAt(elapsedSec: Int): EscalationState {
        val t = elapsedSec.coerceAtLeast(0)
        val lastIndex = profile.stages.lastIndex

        var index = 0
        for (i in profile.stages.indices) {
            if (t >= starts[i]) index = i else break
        }
        // Anything past the start of the last stage belongs to the last stage.
        if (t >= starts[lastIndex]) index = lastIndex

        val stage = profile.stages[index]
        val inStage = t - starts[index]
        val isFinal = index == lastIndex

        val progress = when {
            isFinal -> 1f
            stage.durationSec <= 0 -> 1f
            else -> (inStage.toFloat() / stage.durationSec).coerceIn(0f, 1f)
        }

        return EscalationState(
            stageIndex = index,
            stage = stage,
            elapsedSec = t,
            elapsedInStageSec = inStage,
            stageProgress = progress,
            audioLevel = if (stage.audio.enabled) stage.audio.levelAt(progress) else 0f,
            isFinalStage = isFinal,
        )
    }
}
