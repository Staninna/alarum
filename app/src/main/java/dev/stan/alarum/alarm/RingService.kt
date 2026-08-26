package dev.stan.alarum.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import dev.stan.alarum.AlarumApp
import dev.stan.alarum.R
import dev.stan.alarum.domain.Alarm
import dev.stan.alarum.domain.Challenge
import dev.stan.alarum.domain.EscalationEngine
import dev.stan.alarum.domain.Schedule
import dev.stan.alarum.effect.AudioEffector
import dev.stan.alarum.effect.HapticEffector
import dev.stan.alarum.effect.TorchEffector
import dev.stan.alarum.ha.AlarumState
import dev.stan.alarum.ui.ring.RingActivity
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs one ring from start to dismissal.
 *
 * The loop is deliberately dumb: tick once a second, ask the pure
 * [EscalationEngine] what the world should look like, make it so. All the
 * judgement lives in the engine where it can be tested; this class only knows
 * how to talk to speakers, motors and Home Assistant.
 *
 * Home Assistant work is fired into a separate scope and never awaited. If the
 * broker is down the ring is unaffected.
 */
class RingService : Service() {

    private val app: AlarumApp get() = application as AlarumApp

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val haScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var audio: AudioEffector
    private lateinit var haptics: HapticEffector
    private lateinit var torch: TorchEffector

    private var wakeLock: PowerManager.WakeLock? = null
    private var loop: Job? = null
    private var alarm: Alarm? = null
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        audio = AudioEffector(this)
        haptics = HapticEffector(this)
        torch = TorchEffector(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
                startForeground(NOTIFICATION_ID, buildNotification("Alarm", "Waking up"))
                if (id != null && loop == null) begin(id)
            }
            ACTION_DISMISS -> finishRing(snooze = false)
            ACTION_SNOOZE -> finishRing(snooze = true)
        }
        return START_STICKY
    }

    private fun begin(alarmId: String) {
        acquireWakeLock()
        startedAt = System.currentTimeMillis()

        Ringing.onDismiss = { startSelf(ACTION_DISMISS) }
        Ringing.onSnooze = { startSelf(ACTION_SNOOZE) }

        loop = scope.launch {
            app.repository.loadIfNeeded()
            val a = app.repository.alarm(alarmId) ?: run {
                Log.w(TAG, "no alarm $alarmId; stopping")
                stopSelf()
                return@launch
            }
            alarm = a
            val profile = app.repository.profileOrDefault(a.profileId)
            val engine = EscalationEngine(profile)

            haScope.launch { app.publisher.openSession() }
            audio.start()
            launchRingScreen()

            var lastStage = -1
            while (isActive) {
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                val state = engine.stateAt(elapsed)

                if (state.stageIndex != lastStage) {
                    lastStage = state.stageIndex
                    onStageEntered(a, profile.name, engine, state.stageIndex)
                }

                audio.update(state.stage.audio.sound, state.audioLevel)
                haptics.apply(state.stage.haptics)
                torch.apply(state.stage.flash.torchHz)

                val nextStart = if (state.isFinalStage) null
                else engine.stageStartSec(state.stageIndex + 1)

                Ringing.publish(
                    RingUiState(
                        alarmId = a.id,
                        alarmLabel = a.label.ifBlank { "Alarm" },
                        stageName = state.stage.name,
                        stageIndex = state.stageIndex,
                        totalStages = engine.stageCount,
                        stageProgress = state.stageProgress,
                        elapsedSec = elapsed,
                        secondsToNextStage = nextStart?.let { (it - elapsed).coerceAtLeast(0) },
                        isFinalStage = state.isFinalStage,
                        allowSnooze = state.stage.allowSnooze,
                        snoozeMinutes = a.snoozeMinutes,
                        screenBrightness = state.stage.flash.screenBrightness,
                        audioLevel = state.audioLevel,
                        challenge = challengeFor(a, state.stageIndex, state.stage.dismissal),
                    ),
                )

                if (elapsed % 5 == 0) publishState(a, profile.name, state.stage.name, state.stageIndex, engine.stageCount, elapsed, state.isFinalStage)

                updateNotification(a, state.stage.name)
                delay(1000)
            }
        }
    }

    /** Fired once when a stage begins: system volume takeover and the optional HA script. */
    private fun onStageEntered(
        a: Alarm,
        profileName: String,
        engine: EscalationEngine,
        index: Int,
    ) {
        val stage = engine.profile.stages[index]
        audio.commandeerSystemVolume(stage.audio.commandeerSystemVolume)
        publishState(a, profileName, stage.name, index, engine.stageCount, 0, index == engine.stageCount - 1)
        stage.haScript?.takeIf { it.isNotBlank() }?.let { entity ->
            haScope.launch { app.haRest.runEntity(entity) }
        }
        Log.i(TAG, "stage $index: ${stage.name}")
    }

    private fun challengeFor(a: Alarm, stageIndex: Int, spec: dev.stan.alarum.domain.DismissalSpec) =
        Challenge.of(
            spec = spec,
            enrolledTagId = app.repository.settings.value.nfcTagId,
            seed = a.id.hashCode().toLong() * 31 + stageIndex,
        )

    private fun publishState(
        a: Alarm,
        profileName: String,
        stageName: String,
        index: Int,
        total: Int,
        elapsed: Int,
        finalStage: Boolean,
    ) {
        haScope.launch {
            app.publisher.publishLive(
                AlarumState(
                    nextAlarm = app.scheduler.nextAcross()?.second
                        ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    ringing = "ON",
                    stage = stageName,
                    stageSlug = AlarumState.slug(stageName),
                    stageIndex = index,
                    totalStages = total,
                    finalStage = finalStage,
                    elapsedSec = elapsed,
                    alarmLabel = a.label.ifBlank { "Alarm" },
                    alarmId = a.id,
                    profile = profileName,
                ),
            )
        }
    }

    private fun finishRing(snooze: Boolean) {
        val a = alarm
        loop?.cancel()
        loop = null

        audio.stop()
        haptics.stop()
        torch.stop()
        Ringing.publish(null)
        Ringing.onDismiss = null
        Ringing.onSnooze = null

        val nowSec = System.currentTimeMillis() / 1000
        if (a != null) {
            if (snooze) {
                app.scheduler.snooze(a.id, a.snoozeMinutes)
            } else {
                app.repository.updateSettings { it.copy(lastDismissedEpochSec = nowSec) }
                // A one-shot alarm has done its job; a repeating one moves on.
                if (a.isRepeating) app.scheduler.schedule(a)
                else app.repository.upsertAlarm(a.copy(enabled = false))
            }
        }

        haScope.launch {
            app.publisher.publishLive(
                AlarumState(
                    nextAlarm = app.scheduler.nextAcross()?.second
                        ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    ringing = "OFF",
                    stage = if (snooze) "snoozed" else "idle",
                    stageSlug = if (snooze) "snoozed" else "idle",
                    stageIndex = -1,
                    lastDismissed = if (snooze) null else ZonedDateTime
                        .ofInstant(Instant.ofEpochSecond(nowSec), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    snoozedUntil = if (snooze && a != null) {
                        ZonedDateTime.now().plusMinutes(a.snoozeMinutes.toLong())
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    } else null,
                ),
            )
            app.publisher.closeSession()
        }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startSelf(action: String) {
        startForegroundService(Intent(this, RingService::class.java).apply { this.action = action })
    }

    private fun launchRingScreen() {
        runCatching {
            startActivity(
                Intent(this, RingActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ),
            )
        }
    }

    private fun fullScreenIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(title: String, text: String): Notification =
        Notification.Builder(this, AlarumApp.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenIntent(), true)
            .setContentIntent(fullScreenIntent())
            .build()

    private fun updateNotification(a: Alarm, stage: String) {
        runCatching {
            getSystemService(android.app.NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification(a.label.ifBlank { "Alarm" }, stage),
            )
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarum:ring").apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        audio.stop()
        haptics.stop()
        torch.stop()
        releaseWakeLock()
        job.cancel()
        Ringing.publish(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.stan.alarum.RING_START"
        const val ACTION_DISMISS = "dev.stan.alarum.RING_DISMISS"
        const val ACTION_SNOOZE = "dev.stan.alarum.RING_SNOOZE"
        private const val NOTIFICATION_ID = 4711
        private const val TAG = "AlarumRing"
    }
}
