package dev.stan.alarum.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The contract between this app and Home Assistant.
 *
 * Everything the house needs to know is in here. Automations trigger off these
 * fields; the app has no opinion about what they do with them. Adding a field
 * is safe, renaming one breaks someone's automation, so treat the names as API.
 */
@Serializable
data class AlarumState(
    /** ISO-8601 with offset, or null when nothing is scheduled. */
    @SerialName("next_alarm") val nextAlarm: String? = null,
    @SerialName("next_alarm_label") val nextAlarmLabel: String? = null,
    @SerialName("ringing") val ringing: String = "OFF",
    /** Human stage name, e.g. "Gentle". "idle" when not ringing. */
    @SerialName("stage") val stage: String = "idle",
    /** Lowercase slug of the stage name, stable for automation conditions. */
    @SerialName("stage_slug") val stageSlug: String = "idle",
    /** -1 when idle, otherwise 0-based. */
    @SerialName("stage_index") val stageIndex: Int = -1,
    @SerialName("total_stages") val totalStages: Int = 0,
    @SerialName("final_stage") val finalStage: Boolean = false,
    @SerialName("elapsed_sec") val elapsedSec: Int = 0,
    @SerialName("alarm_label") val alarmLabel: String? = null,
    @SerialName("alarm_id") val alarmId: String? = null,
    @SerialName("profile") val profile: String? = null,
    @SerialName("last_dismissed") val lastDismissed: String? = null,
    @SerialName("snoozed_until") val snoozedUntil: String? = null,
    /**
     * True while the in-app previewer is driving this, rather than a real
     * alarm. Automations that would rather not open the curtains at 15:00 can
     * condition on it; ones that want to be rehearsed can ignore it.
     */
    @SerialName("preview") val preview: Boolean = false,
) {
    companion object {
        fun slug(name: String): String =
            name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                .ifEmpty { "stage" }
    }
}
