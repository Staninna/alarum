package dev.stan.alarum.data

import android.content.Context
import dev.stan.alarum.domain.Alarm
import dev.stan.alarum.domain.Defaults
import dev.stan.alarum.domain.EscalationProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

/**
 * Single source of truth for alarms, escalation profiles and settings.
 *
 * Deliberately not a Room database. There are a handful of alarms and a handful
 * of profiles; the whole state fits in memory, and keeping it as plain
 * serializable data means the domain model stays free of persistence
 * annotations and the escalation engine stays testable on the JVM.
 */
class AlarumRepository(context: Context, private val scope: CoroutineScope) {

    private val app = context.applicationContext

    private val alarmStore = JsonStore(app, "alarms.json", ListSerializer(serializer<Alarm>())) {
        emptyList()
    }
    private val profileStore =
        JsonStore(app, "profiles.json", ListSerializer(serializer<EscalationProfile>())) {
            Defaults.all()
        }
    private val settingsStore = JsonStore(app, "settings.json", serializer<AppSettings>()) {
        AppSettings()
    }

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _profiles = MutableStateFlow<List<EscalationProfile>>(emptyList())
    val profiles: StateFlow<List<EscalationProfile>> = _profiles.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() {
        _alarms.value = alarmStore.read()
        _profiles.value = profileStore.read().ifEmpty { Defaults.all() }
        _settings.value = settingsStore.read()
        _loaded.value = true
    }

    /** Blocking-safe read for use from a BroadcastReceiver's goAsync scope. */
    suspend fun loadIfNeeded() {
        if (!_loaded.value) load()
    }

    fun alarm(id: String): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun profile(id: String): EscalationProfile? =
        _profiles.value.firstOrNull { it.id == id }

    /** Falls back rather than failing: a missing profile must never mean no alarm. */
    fun profileOrDefault(id: String): EscalationProfile =
        profile(id) ?: _profiles.value.firstOrNull() ?: Defaults.gentleThenBrutal()

    fun upsertAlarm(alarm: Alarm) {
        val next = _alarms.value.filterNot { it.id == alarm.id } + alarm
        _alarms.value = next.sortedWith(compareBy({ it.hour }, { it.minute }))
        persistAlarms()
    }

    fun deleteAlarm(id: String) {
        _alarms.value = _alarms.value.filterNot { it.id == id }
        persistAlarms()
    }

    fun upsertProfile(profile: EscalationProfile) {
        val next = _profiles.value.map { if (it.id == profile.id) profile else it }
        _profiles.value = if (next.any { it.id == profile.id }) next else next + profile
        scope.launch { profileStore.write(_profiles.value) }
    }

    fun deleteProfile(id: String) {
        if (_profiles.value.size <= 1) return
        _profiles.value = _profiles.value.filterNot { it.id == id }
        scope.launch { profileStore.write(_profiles.value) }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        _settings.value = block(_settings.value)
        scope.launch { settingsStore.write(_settings.value) }
    }

    private fun persistAlarms() {
        scope.launch { alarmStore.write(_alarms.value) }
    }
}
