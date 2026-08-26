package dev.stan.alarum.alarm

import android.content.Context
import android.util.Log
import dev.stan.alarum.domain.DismissalSpec
import dev.stan.alarum.domain.EscalationProfile
import dev.stan.alarum.domain.HapticSpec
import dev.stan.alarum.domain.PreviewSpeed
import dev.stan.alarum.domain.PreviewTimeline
import dev.stan.alarum.domain.VibePattern
import dev.stan.alarum.effect.AudioEffector
import dev.stan.alarum.effect.HapticEffector
import dev.stan.alarum.effect.TorchEffector
import dev.stan.alarum.ha.AlarumState
import dev.stan.alarum.ha.HaRest
import dev.stan.alarum.ha.HaResult
import dev.stan.alarum.ha.StatePublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the preview screen needs to draw itself. */
data class PreviewUiState(
    val profileName: String,
    val stageName: String,
    val stageSlug: String,
    val stageIndex: Int,
    val totalStages: Int,
    val elapsedSec: Int,
    val totalSec: Int,
    val fraction: Float,
    val secondsToNextStage: Int?,
    val isFinalStage: Boolean,
    val inTail: Boolean,
    val audioLevel: Float,
    val sound: String,
    val commandeersVolume: Boolean,
    val haptics: HapticSpec,
    val screenBrightness: Float,
    val torchHz: Float,
    val dismissal: DismissalSpec,
    val allowSnooze: Boolean,
    val haScript: String?,
    val playing: Boolean,
    val atEnd: Boolean,
    val muted: Boolean,
    val speed: PreviewSpeed,
    val publishing: Boolean,
    val route: String,
    /** Lights photographed at the start, null when nothing was captured. */
    val snapshotCount: Int?,
    val snapshotProblem: String?,
)

/**
 * Runs a profile the way [RingService] would, but on a clock you control.
 *
 * Same engine, same effectors, so what you hear and feel is what 07:00 will
 * hear and feel. What is different is deliberate and small:
 *
 * - time is yours — pause it, scrub it, or run it at 60×;
 * - the system alarm volume is never commandeered, because a preview that pins
 *   your alarm stream to maximum and relies on a clean exit to put it back is a
 *   bad neighbour. The stage that would do it says so instead;
 * - published state carries `preview: true` so automations can tell the
 *   rehearsal from the performance;
 * - a stage's HA script is not run. State is a claim about the world, a script
 *   is an action on it, and nobody wants the bedroom lights at 15:00.
 *
 * Tied to the screen rather than a foreground service: leave the previewer and
 * the noise stops, which is the opposite of what a real alarm should do and
 * exactly right for this.
 */
class PreviewSession(
    context: Context,
    private val publisher: StatePublisher,
    /**
     * The scheduled next alarm, ISO-8601. Every publish carries the whole
     * state object, so omitting this would blank `sensor.alarum_next_alarm`
     * for as long as the preview ran.
     */
    private val nextAlarm: () -> String?,
    private val haRest: HaRest,
) {

    private val audio = AudioEffector(context)
    private val haptics = HapticEffector(context)
    private val torch = TorchEffector(context)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val haScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loop: Job? = null

    private val _state = MutableStateFlow<PreviewUiState?>(null)
    val state: StateFlow<PreviewUiState?> = _state.asStateFlow()

    private var timeline: PreviewTimeline? = null
    @Volatile private var positionMs = 0L
    @Volatile private var playing = false
    @Volatile private var muted = false
    @Volatile private var speed = PreviewSpeed.default
    @Volatile private var publishing = false
    @Volatile private var snapshotCount: Int? = null
    @Volatile private var snapshotProblem: String? = null

    val isRunning: Boolean get() = loop != null

    /**
     * Begin previewing [profile]. Refuses while a real alarm is ringing —
     * two things driving the speaker and the stage sensor at once would make a
     * mess of both.
     */
    fun start(profile: EscalationProfile, publishToHa: Boolean) {
        if (loop != null) stop()
        if (Ringing.isRinging) {
            Log.w(TAG, "not previewing: an alarm is actually ringing")
            return
        }
        val line = PreviewTimeline(profile)
        timeline = line
        positionMs = 0L
        playing = true
        muted = false
        // Home Assistant runs in wall-clock time: a light with a 290-second
        // transition does not care that the app's clock is at 10x, it just
        // gets restarted a tenth of the way in. Previewing the house means
        // previewing at the speed the house runs at.
        speed = if (publishToHa) PreviewSpeed.REAL else PreviewSpeed.default
        publishing = publishToHa

        snapshotCount = null
        snapshotProblem = null

        audio.start()
        if (publishing) {
            haScope.launch { publisher.openSession() }
            haScope.launch { snapshotHouse() }
        }

        loop = scope.launch {
            var lastTickNs = System.nanoTime()
            var lastStage = -1
            var lastPublishMs = 0L

            while (isActive) {
                val nowNs = System.nanoTime()
                val wallDeltaMs = (nowNs - lastTickNs) / 1_000_000
                lastTickNs = nowNs

                val totalMs = line.totalSec * 1000L
                if (playing) {
                    positionMs += (wallDeltaMs * speed.multiplier).toLong()
                    if (positionMs >= totalMs) {
                        positionMs = totalMs
                        playing = false
                    }
                }

                val sec = (positionMs / 1000L).toInt()
                val es = line.stateAt(sec)
                val stage = es.stage

                audio.update(stage.audio.sound, if (muted) 0f else es.audioLevel)
                haptics.apply(if (muted) SILENT else stage.haptics)
                torch.apply(stage.flash.torchHz)

                val stageChanged = es.stageIndex != lastStage
                if (stageChanged) lastStage = es.stageIndex

                val nowMs = System.currentTimeMillis()
                if (publishing && (stageChanged || nowMs - lastPublishMs >= PUBLISH_EVERY_MS)) {
                    lastPublishMs = nowMs
                    publish(line, es.stageIndex, stage.name, sec, es.isFinalStage)
                }

                val nextStart = if (es.isFinalStage) null else line.engine.stageStartSec(es.stageIndex + 1)
                _state.value = PreviewUiState(
                    profileName = line.profile.name,
                    stageName = stage.name,
                    stageSlug = AlarumState.slug(stage.name),
                    stageIndex = es.stageIndex,
                    totalStages = line.engine.stageCount,
                    elapsedSec = sec,
                    totalSec = line.totalSec,
                    fraction = line.fractionOf(sec),
                    secondsToNextStage = nextStart?.let { (it - sec).coerceAtLeast(0) },
                    isFinalStage = es.isFinalStage,
                    inTail = sec >= line.rampSec,
                    audioLevel = if (muted) 0f else es.audioLevel,
                    sound = stage.audio.sound,
                    commandeersVolume = stage.audio.commandeerSystemVolume,
                    haptics = stage.haptics,
                    screenBrightness = stage.flash.screenBrightness,
                    torchHz = stage.flash.torchHz,
                    dismissal = stage.dismissal,
                    allowSnooze = stage.allowSnooze,
                    haScript = stage.haScript?.takeIf { it.isNotBlank() },
                    playing = playing,
                    atEnd = positionMs >= totalMs,
                    muted = muted,
                    speed = speed,
                    publishing = publishing,
                    route = publisher.describeRoute(),
                    snapshotCount = snapshotCount,
                    snapshotProblem = snapshotProblem,
                )

                delay(TICK_MS)
            }
        }
    }

    fun setPlaying(play: Boolean) {
        val line = timeline ?: return
        // Pressing play at the very end starts it over rather than doing nothing.
        if (play && positionMs >= line.totalSec * 1000L) positionMs = 0L
        playing = play
    }

    fun seekTo(fraction: Float) {
        val line = timeline ?: return
        positionMs = (fraction.coerceIn(0f, 1f) * line.totalSec * 1000L).toLong()
    }

    /** Jump to the start of a stage, for when only the last one is in question. */
    fun seekToStage(index: Int) {
        val line = timeline ?: return
        positionMs = line.engine.stageStartSec(index) * 1000L
    }

    fun setSpeed(s: PreviewSpeed) {
        speed = s
    }

    fun setMuted(m: Boolean) {
        muted = m
    }

    fun setPublishing(p: Boolean) {
        if (publishing == p) return
        publishing = p
        if (p) speed = PreviewSpeed.REAL
        haScope.launch {
            if (p) {
                publisher.openSession()
                snapshotHouse()
            } else {
                restoreHouse()
                publishIdle()
            }
        }
    }

    fun stop() {
        val wasPublishing = publishing
        loop?.cancel()
        loop = null
        timeline = null
        playing = false

        audio.stop()
        haptics.stop()
        torch.stop()
        _state.value = null

        if (wasPublishing) {
            haScope.launch {
                // Idle first, restore second: going idle can set an automation
                // going, and the photograph should have the last word on what
                // the room looks like.
                publishIdle()
                restoreHouse()
                publisher.closeSession()
            }
        }
    }

    /**
     * Fire the stand-down for real, on purpose.
     *
     * The only thing in the previewer that moves `binary_sensor.alarum_ringing`,
     * and it does the whole arc — on, then off — because the dismissal
     * automation triggers on that edge and there is no edge without both ends.
     * Anything it sets is meant to stay, so the lights are not put back.
     */
    fun dismiss() {
        val line = timeline
        val wasPublishing = publishing
        val stageName = _state.value?.stageName ?: "idle"
        val index = _state.value?.stageIndex ?: -1

        // Leaving the screen calls stop() straight after this, which would
        // restore the lights and undo the dismissal we just ran on purpose.
        // Standing down from here is this method's job alone.
        publishing = false
        snapshotCount = null

        loop?.cancel()
        loop = null
        timeline = null
        playing = false
        audio.stop()
        haptics.stop()
        torch.stop()
        _state.value = null

        if (!wasPublishing) return
        haScope.launch {
            publisher.publishLive(
                AlarumState(
                    nextAlarm = nextAlarm(),
                    ringing = "ON",
                    stage = stageName,
                    stageSlug = AlarumState.slug(stageName),
                    stageIndex = index,
                    totalStages = line?.engine?.stageCount ?: 0,
                    alarmLabel = "Preview",
                    profile = line?.profile?.name,
                    preview = true,
                ),
            )
            // Long enough for Home Assistant to register the "on" before the
            // "off", short enough that nobody is standing there waiting.
            delay(DISMISS_EDGE_MS)
            publishIdle()
            publisher.closeSession()
        }
    }

    /**
     * Photograph the lights so [restoreHouse] can put them back. Best effort:
     * a preview whose house cannot be captured still runs, it just says so.
     */
    private suspend fun snapshotHouse() {
        when (val r = haRest.snapshotLights(RESTORE_SCENE)) {
            is HaResult.Ok -> {
                snapshotCount = r.value
                snapshotProblem = if (r.value == 0) "Home Assistant reported no lights" else null
            }
            is HaResult.Failed -> {
                snapshotCount = null
                snapshotProblem = r.reason
            }
        }
    }

    private suspend fun restoreHouse() {
        if ((snapshotCount ?: 0) <= 0) return
        haRest.restoreScene(RESTORE_SCENE)
    }

    private fun publish(
        line: PreviewTimeline,
        index: Int,
        stageName: String,
        elapsed: Int,
        finalStage: Boolean,
    ) {
        haScope.launch {
            publisher.publishLive(
                AlarumState(
                    // Deliberately OFF for the whole preview. Any on-to-off
                    // edge would fire the stand-down automation on the way out,
                    // and a rehearsal that triggers things you cannot untrigger
                    // is not a rehearsal. Stage state is what automations key
                    // off anyway; the dismissal is its own explicit button.
                    ringing = "OFF",
                    stage = stageName,
                    stageSlug = AlarumState.slug(stageName),
                    stageIndex = index,
                    totalStages = line.engine.stageCount,
                    finalStage = finalStage,
                    elapsedSec = elapsed,
                    alarmLabel = "Preview",
                    profile = line.profile.name,
                    nextAlarm = nextAlarm(),
                    preview = true,
                ),
            )
        }
    }

    /**
     * Put the house back. A preview that leaves `binary_sensor.alarum_ringing`
     * stuck on is worse than one that never published at all.
     */
    private suspend fun publishIdle() {
        publisher.publishLive(
            AlarumState(
                nextAlarm = nextAlarm(),
                ringing = "OFF",
                stage = "idle",
                stageSlug = "idle",
                stageIndex = -1,
                preview = true,
            ),
        )
    }

    private companion object {
        const val TAG = "AlarumPreview"

        /** 20 Hz: fine enough that a 60× scrub still looks continuous. */
        const val TICK_MS = 50L

        /** Wall clock, not preview clock — 60× must not mean 60× the publishes. */
        const val PUBLISH_EVERY_MS = 1000L

        /** Reused every preview, so HA collects one spare scene rather than dozens. */
        const val RESTORE_SCENE = "alarum_preview_restore"

        const val DISMISS_EDGE_MS = 1200L

        val SILENT = HapticSpec(VibePattern.NONE)
    }
}
