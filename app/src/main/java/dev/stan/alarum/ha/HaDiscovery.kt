package dev.stan.alarum.ha

import dev.stan.alarum.data.HaSettings

/**
 * Retained MQTT discovery payloads.
 *
 * Retained is the point: it is what makes these entities survive a Home
 * Assistant restart. Posting to /api/states would create the same sensors and
 * lose them on the next reboot.
 *
 * Everything shares one state topic and picks its value out with a template, so
 * a single publish updates the whole set atomically — no window where the stage
 * has advanced but "ringing" has not.
 */
object HaDiscovery {

    private fun device(s: HaSettings, nodeId: String) = """
        "device":{"identifiers":["alarum_$nodeId"],"name":"${s.deviceName}",
        "manufacturer":"Alarum","model":"Android alarm","sw_version":"1.0"}
    """.trimIndent().replace("\n", "")

    /**
     * Home Assistant builds the entity id from the device name plus the entity
     * name and ignores `object_id` for discovered entities, so the names below
     * are load-bearing: device "Alarum" + name "Stage" is what produces
     * `sensor.alarum_stage`. Rename the device and every id moves with it.
     */
    /** Timestamp sensors need "None" rather than an empty string to read as unknown. */
    private fun nullable(field: String) =
        "{{ value_json.$field if value_json.$field else 'None' }}"

    fun configs(s: HaSettings, nodeId: String): List<Pair<String, String>> {
        val prefix = s.discoveryPrefix.trim('/').ifBlank { "homeassistant" }
        val stateTopic = "alarum/$nodeId/state"
        val dev = device(s, nodeId)
        val uid = "alarum_$nodeId"

        fun cfg(
            component: String,
            key: String,
            name: String,
            template: String,
            extra: String = "",
        ): Pair<String, String> {
            val topic = "$prefix/$component/$uid/$key/config"
            val payload = buildString {
                append("{")
                append(""""name":"$name",""")
                append(""""unique_id":"${uid}_$key",""")
                // Pin the entity id rather than letting HA derive it from the
                // device and entity names. Without this the ids drift with the
                // device name, and collide with any left over from the REST
                // fallback — which quietly breaks every automation.
                append(""""object_id":"${nodeId}_$key",""")
                append(""""state_topic":"$stateTopic",""")
                append(""""value_template":"${template.replace("\"", "\\\"")}",""")
                if (extra.isNotBlank()) append("$extra,")
                append(dev)
                append("}")
            }
            return topic to payload
        }

        return listOf(
            cfg(
                "sensor", "next_alarm", "Next alarm", nullable("next_alarm"),
                // Attributes too, so awake_by rides along with the start time
                // rather than needing an entity of its own.
                """"device_class":"timestamp","icon":"mdi:alarm","json_attributes_topic":"$stateTopic"""",
            ),
            cfg(
                "binary_sensor", "ringing", "Ringing", "{{ value_json.ringing }}",
                // Attributes as well as state, so an automation can check
                // `preview` on the entity it already triggers off rather than
                // reaching across to the stage sensor.
                """"payload_on":"ON","payload_off":"OFF","icon":"mdi:bell-ring","json_attributes_topic":"$stateTopic"""",
            ),
            cfg(
                "sensor", "stage", "Stage", "{{ value_json.stage_slug }}",
                """"icon":"mdi:stairs-up","json_attributes_topic":"$stateTopic"""",
            ),
            cfg(
                "sensor", "say", "Say", "{{ value_json.say if value_json.say else 'idle' }}",
                // Attributes carry say_seq, which is what an automation should
                // trigger on: the same line twice running is two utterances,
                // and the state alone would not change between them.
                """"icon":"mdi:account-voice","json_attributes_topic":"$stateTopic"""",
            ),
            cfg(
                "sensor", "stage_index", "Stage index",
                "{{ value_json.stage_index }}",
                """"icon":"mdi:numeric","state_class":"measurement"""",
            ),
            cfg(
                "sensor", "elapsed", "Elapsed", "{{ value_json.elapsed_sec }}",
                """"unit_of_measurement":"s","icon":"mdi:timer-outline","state_class":"measurement"""",
            ),
            cfg(
                "sensor", "last_dismissed", "Last dismissed", nullable("last_dismissed"),
                """"device_class":"timestamp","icon":"mdi:bell-off"""",
            ),
        )
    }
}
