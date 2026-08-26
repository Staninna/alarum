package dev.stan.alarum.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.stan.alarum.AlarumApp
import dev.stan.alarum.alarm.PreviewSession
import dev.stan.alarum.data.AppSettings
import dev.stan.alarum.data.HaSettings
import dev.stan.alarum.domain.Alarm
import dev.stan.alarum.domain.EscalationProfile
import dev.stan.alarum.domain.SpeechSpec
import dev.stan.alarum.ha.AlarumState
import dev.stan.alarum.ha.HaEntity
import dev.stan.alarum.ha.HaResult
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarumViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AlarumApp
    private val repo = app.repository

    val alarms: StateFlow<List<Alarm>> = repo.alarms
    val profiles: StateFlow<List<EscalationProfile>> = repo.profiles
    val settings: StateFlow<AppSettings> = repo.settings

    /**
     * Rehearsing a profile without arming an alarm. Owned by the view model so
     * a rotation does not restart the ramp, and torn down with it so nothing
     * outlives the screen that started it.
     */
    private var previewSession: PreviewSession? = null
    val preview: PreviewSession
        get() = previewSession ?: PreviewSession(
            context = app,
            publisher = app.publisher,
            nextAlarm = {
                app.scheduler.nextAcross()?.second
                    ?.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            },
            haRest = app.haRest,
        ).also { previewSession = it }

    private val _speechProblem = MutableStateFlow<String?>(null)
    /** What happened the last time "Say one now" was pressed. */
    val speechProblem: StateFlow<String?> = _speechProblem.asStateFlow()

    /**
     * Hand one line to the house right now, without running a stage.
     *
     * Marked as a preview so an automation can tell a test from a morning. If
     * nothing says it, the automation is missing rather than the app being
     * broken — the phone's only job here is to publish.
     */
    fun sayNow(spec: SpeechSpec) {
        val line = spec.lineAt(0, SAMPLE_SEED) ?: return
        if (!settings.value.ha.mqttConfigured && !settings.value.ha.restConfigured) {
            _speechProblem.value =
                "Home Assistant is not configured, so there is nowhere to send it."
            return
        }
        viewModelScope.launch {
            app.publisher.publish(
                AlarumState(
                    // Every publish carries the whole state object, so omitting
                    // this would blank sensor.alarum_next_alarm every time the
                    // test button was pressed.
                    nextAlarm = app.scheduler.nextAcross()?.second
                        ?.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    ringing = "OFF",
                    stage = "idle",
                    stageSlug = "idle",
                    stageIndex = -1,
                    say = line,
                    saySeq = System.currentTimeMillis(),
                    preview = true,
                ),
            )
            _speechProblem.value =
                "Sent “$line” via ${app.publisher.describeRoute()}. " +
                "If nothing said it, the automation is what is missing."
        }
    }

    private val _entities = MutableStateFlow<List<HaEntity>>(emptyList())
    val entities: StateFlow<List<HaEntity>> = _entities.asStateFlow()

    private val _connectionMessage = MutableStateFlow<String?>(null)
    val connectionMessage: StateFlow<String?> = _connectionMessage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun newAlarm(): Alarm = Alarm(
        id = UUID.randomUUID().toString(),
        hour = 7,
        minute = 0,
        days = dev.stan.alarum.domain.Schedule.weekdays,
        profileId = profiles.value.firstOrNull()?.id
            ?: dev.stan.alarum.domain.Defaults.GENTLE_ID,
    )

    fun save(alarm: Alarm) {
        repo.upsertAlarm(alarm)
        app.scheduler.cancel(alarm.id)
        if (alarm.enabled) app.scheduler.schedule(alarm)
        app.scheduler.publishNextAlarm()
    }

    fun delete(alarmId: String) {
        app.scheduler.cancel(alarmId)
        repo.deleteAlarm(alarmId)
        app.scheduler.publishNextAlarm()
    }

    fun toggle(alarm: Alarm, enabled: Boolean) = save(alarm.copy(enabled = enabled, skipNext = false))

    fun saveProfile(profile: EscalationProfile) {
        repo.upsertProfile(profile)
    }

    fun deleteProfile(id: String) = repo.deleteProfile(id)

    fun restoreDefaultProfiles() {
        val n = repo.restoreDefaultProfiles()
        _connectionMessage.value =
            "Restored $n shipped profiles. Anything you made yourself was left alone."
    }

    fun duplicateProfile(profile: EscalationProfile) {
        repo.upsertProfile(
            profile.copy(
                id = UUID.randomUUID().toString(),
                name = "${profile.name} copy",
            ),
        )
    }

    fun updateHa(block: (HaSettings) -> HaSettings) {
        repo.updateSettings { it.copy(ha = block(it.ha)) }
        app.mqtt.invalidateDiscovery()
    }

    fun setNodeId(id: String) {
        repo.updateSettings { it.copy(nodeId = id.ifBlank { "alarum" }) }
        app.mqtt.invalidateDiscovery()
    }

    fun enrollNfcTag(tagId: String?) = repo.updateSettings { it.copy(nfcTagId = tagId) }

    fun testRest() {
        _busy.value = true
        viewModelScope.launch {
            _connectionMessage.value = when (val r = app.haRest.ping()) {
                is HaResult.Ok -> "REST: ${r.value}"
                is HaResult.Failed -> "REST failed: ${r.reason}"
            }
            _busy.value = false
        }
    }

    fun testMqtt() {
        _busy.value = true
        viewModelScope.launch {
            _connectionMessage.value = "MQTT: " + app.mqtt.test()
            _busy.value = false
        }
    }

    /** Push discovery and current state now, so HA has the entities immediately. */
    fun publishNow() {
        app.mqtt.invalidateDiscovery()
        app.scheduler.publishNextAlarm()
        _connectionMessage.value = "Published via ${app.publisher.describeRoute()}"
    }

    /** Which route the state is currently taking, for the settings screen. */
    fun route(): String = app.publisher.describeRoute()

    fun loadEntities() {
        _busy.value = true
        viewModelScope.launch {
            when (val r = app.haRest.states()) {
                is HaResult.Ok -> {
                    _entities.value = r.value
                    _connectionMessage.value = "Loaded ${r.value.size} entities"
                }
                is HaResult.Failed -> _connectionMessage.value = "Could not load entities: ${r.reason}"
            }
            _busy.value = false
        }
    }

    fun clearMessage() {
        _connectionMessage.value = null
    }

    override fun onCleared() {
        previewSession?.stop()
        super.onCleared()
    }

    /** Debug affordance: ring in a few seconds so the ramp can be tested awake. */
    fun testRing(alarm: Alarm) {
        repo.upsertAlarm(alarm)
        app.scheduler.snooze(alarm.id, 0)
    }

    private companion object {
        const val SAMPLE_SEED = 11L
    }
}
