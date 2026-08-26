package dev.stan.alarum.data

import kotlinx.serialization.Serializable

@Serializable
data class HaSettings(
    /** e.g. https://homeassistant.example.com — no trailing slash, no /api suffix. */
    val baseUrl: String = "",
    val token: String = "",
    val mqttHost: String = "",
    val mqttPort: Int = 1883,
    val mqttUser: String = "",
    val mqttPassword: String = "",
    val mqttTls: Boolean = false,
    /** Prefix for discovery topics. HA's default is "homeassistant". */
    val discoveryPrefix: String = "homeassistant",
    /** Shown as the device name in HA. */
    val deviceName: String = "Alarum",
) {
    val restConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
    val mqttConfigured: Boolean get() = mqttHost.isNotBlank()
    val apiBase: String get() = baseUrl.trimEnd('/') + "/api"
}

@Serializable
data class AppSettings(
    val ha: HaSettings = HaSettings(),
    /**
     * Distinguishes this phone in Home Assistant, and forms the entity ids:
     * a nodeId of "alarum" produces `sensor.alarum_stage`.
     *
     * Deliberately a stable slug rather than a generated id — a random one would
     * orphan every entity on reinstall and leave `_2` duplicates behind. Change
     * it only if a second phone publishes into the same Home Assistant.
     */
    val nodeId: String = "alarum",
    /** Enrolled NFC tag. Null until you actually own a tag and scan it. */
    val nfcTagId: String? = null,
    val lastDismissedEpochSec: Long? = null,
    val batteryPromptShown: Boolean = false,
)
