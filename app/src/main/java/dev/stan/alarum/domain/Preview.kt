package dev.stan.alarum.domain

/**
 * How fast preview time runs against wall time.
 *
 * A 25-minute ramp watched at 1× is not a preview, it is a morning. 60× turns
 * the same profile into a 25-second trailer, which is the point.
 */
enum class PreviewSpeed(val multiplier: Float, val label: String) {
    REAL(1f, "1×"),
    FAST(10f, "10×"),
    SPRINT(60f, "60×"),
    ;

    companion object {
        val default = FAST
    }
}

/**
 * A ramp with a right-hand edge.
 *
 * The last stage of a profile has no duration — it sustains until the alarm is
 * dealt with — which is exactly the wrong shape for a scrubber. The timeline
 * gives that stage a finite tail so the whole profile fits on a slider, and
 * clamps anything past it rather than running forever.
 *
 * Pure, so the awkward bits (a single-stage profile, a profile whose ramp is
 * zero seconds long) are settled in a unit test rather than on a device.
 */
class PreviewTimeline(val profile: EscalationProfile) {

    val engine = EscalationEngine(profile)

    /** Seconds before the final stage begins. */
    val rampSec: Int = profile.rampSec

    /**
     * How much of the unbounded final stage the preview shows. A share of the
     * ramp so a long profile gets a proportionate look at the ending, with a
     * floor so a short one still gets a full minute of it.
     */
    val tailSec: Int = maxOf(MIN_TAIL_SEC, (rampSec * TAIL_SHARE).toInt())

    val totalSec: Int = rampSec + tailSec

    fun stateAt(sec: Int): EscalationState = engine.stateAt(sec.coerceIn(0, totalSec))

    /** Position on the scrubber, 0..1. */
    fun fractionOf(sec: Int): Float =
        (sec.toFloat() / totalSec.coerceAtLeast(1)).coerceIn(0f, 1f)

    fun secondsAt(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f) * totalSec).toInt().coerceIn(0, totalSec)

    /** Where each stage begins, 0..1, for drawing boundaries under the scrubber. */
    val stageMarks: List<Float> =
        profile.stages.indices.map { fractionOf(engine.stageStartSec(it)) }

    private companion object {
        const val MIN_TAIL_SEC = 60
        const val TAIL_SHARE = 0.25f
    }
}
