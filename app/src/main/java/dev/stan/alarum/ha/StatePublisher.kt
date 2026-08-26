package dev.stan.alarum.ha

import dev.stan.alarum.data.HaSettings

/**
 * Gets [AlarumState] into Home Assistant by whatever route is available.
 *
 * MQTT is the good path: retained discovery means the entities survive a
 * restart of Home Assistant. REST is the path that needs nothing installed —
 * `POST /api/states` invents the same entities, but they are ephemeral and
 * disappear when HA restarts until the app publishes again.
 *
 * Preferring MQTT when it is configured, and falling back rather than failing,
 * means the integration works on day one and gets better when a broker shows
 * up, instead of being all-or-nothing.
 */
class StatePublisher(
    private val settings: () -> HaSettings,
    private val mqtt: MqttPublisher,
    private val rest: HaRest,
) {

    suspend fun openSession() {
        if (settings().mqttConfigured) mqtt.openSession()
    }

    suspend fun closeSession() {
        if (settings().mqttConfigured) mqtt.closeSession()
    }

    /** Called on every stage change and periodically during a ring. */
    suspend fun publishLive(state: AlarumState) {
        if (settings().mqttConfigured) {
            if (mqtt.publishLive(state)) return
        }
        publishOverRest(state)
    }

    /** Called when the schedule changes. */
    suspend fun publish(state: AlarumState) {
        if (settings().mqttConfigured) {
            if (mqtt.publish(state)) return
        }
        publishOverRest(state)
    }

    private suspend fun publishOverRest(state: AlarumState) {
        if (!settings().restConfigured) return
        RestEntities.forEach { entity ->
            rest.setState(
                entityId = entity.entityId,
                state = entity.read(state),
                attributes = entity.attributes(state),
            )
        }
    }

    fun describeRoute(): String = when {
        settings().mqttConfigured -> "MQTT (retained, survives an HA restart)"
        settings().restConfigured -> "REST (works now, entities vanish on an HA restart)"
        else -> "not configured"
    }

    private data class RestEntity(
        val entityId: String,
        val read: (AlarumState) -> String,
        val attributes: (AlarumState) -> Map<String, String>,
    )

    private companion object {
        /**
         * Deliberately the same entity ids MQTT discovery produces, so an
         * automation written against the REST fallback keeps working once a
         * broker arrives.
         */
        val RestEntities = listOf(
            RestEntity(
                "sensor.alarum_next_alarm",
                { it.nextAlarm ?: "unknown" },
                {
                    mapOf(
                        "device_class" to "timestamp",
                        "friendly_name" to "Next alarm",
                        "icon" to "mdi:alarm",
                        "label" to (it.nextAlarmLabel ?: ""),
                    )
                },
            ),
            RestEntity(
                "binary_sensor.alarum_ringing",
                { if (it.ringing == "ON") "on" else "off" },
                { mapOf("friendly_name" to "Ringing", "icon" to "mdi:bell-ring") },
            ),
            RestEntity(
                "sensor.alarum_stage",
                { it.stageSlug },
                {
                    mapOf(
                        "friendly_name" to "Escalation stage",
                        "icon" to "mdi:stairs-up",
                        "stage_name" to it.stage,
                        "stage_index" to it.stageIndex.toString(),
                        "total_stages" to it.totalStages.toString(),
                        "final_stage" to it.finalStage.toString(),
                        "elapsed_sec" to it.elapsedSec.toString(),
                        "alarm_label" to (it.alarmLabel ?: ""),
                        "profile" to (it.profile ?: ""),
                    )
                },
            ),
            RestEntity(
                "sensor.alarum_stage_index",
                { it.stageIndex.toString() },
                { mapOf("friendly_name" to "Escalation stage index", "icon" to "mdi:numeric") },
            ),
            RestEntity(
                "sensor.alarum_elapsed",
                { it.elapsedSec.toString() },
                {
                    mapOf(
                        "friendly_name" to "Ringing for",
                        "unit_of_measurement" to "s",
                        "icon" to "mdi:timer-outline",
                    )
                },
            ),
            RestEntity(
                "sensor.alarum_last_dismissed",
                { it.lastDismissed ?: "unknown" },
                {
                    mapOf(
                        "device_class" to "timestamp",
                        "friendly_name" to "Last dismissed",
                        "icon" to "mdi:bell-off",
                    )
                },
            ),
        )
    }
}
