package dev.stan.alarum.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.stan.alarum.data.AlarumRepository
import dev.stan.alarum.domain.Alarm
import dev.stan.alarum.domain.Schedule
import dev.stan.alarum.ha.AlarumState
import dev.stan.alarum.ha.StatePublisher
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns every interaction with [AlarmManager].
 *
 * Uses `setAlarmClock`, which is the only scheduling API Android promises will
 * fire on time in deep Doze, and which is what puts the alarm glyph in the
 * status bar. Every other exact-alarm API is subject to being deferred, which
 * for an alarm clock is the same thing as being broken.
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: AlarumRepository,
    private val publisher: StatePublisher,
    private val scope: CoroutineScope,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun rescheduleAll() {
        val alarms = repository.alarms.value
        alarms.forEach { cancel(it.id) }
        alarms.filter { it.enabled }.forEach { schedule(it) }
        publishNextAlarm()
    }

    fun schedule(alarm: Alarm) {
        val ring = nextRing(alarm) ?: return
        // May be in the past for an awake-by alarm set with less than a ramp to
        // spare. setAlarmClock fires those at once, which is correct: the
        // deadline still stands, the ramp just gets less of a run-up.
        val at = ring.startsAt.toInstant().toEpochMilli()
        scheduleAt(alarm.id, at, intendedStart = at)
        Log.i(TAG, "scheduled ${alarm.id}: rings $at, awake by ${ring.awakeBy}")
    }

    fun snooze(alarmId: String, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        // A snooze always starts the ramp from the beginning, whatever the
        // alarm is anchored to. You asked for another nine minutes, not for the
        // stage you had already escalated to.
        scheduleAt(alarmId, at, snoozed = true, intendedStart = at)
    }

    private fun nextRing(alarm: Alarm): Schedule.Ring? = Schedule.nextRing(
        alarm = alarm,
        now = ZonedDateTime.now(),
        rampSec = repository.profileOrDefault(alarm.profileId).rampSec,
    )

    private fun scheduleAt(
        alarmId: String,
        epochMillis: Long,
        snoozed: Boolean = false,
        intendedStart: Long = epochMillis,
    ) {
        val show = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            Intent(context, dev.stan.alarum.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(epochMillis, show),
            firePending(alarmId, snoozed, intendedStart),
        )
    }

    fun cancel(alarmId: String) {
        am.cancel(firePending(alarmId, false, 0L))
        am.cancel(firePending(alarmId, true, 0L))
    }

    private fun firePending(alarmId: String, snoozed: Boolean, intendedStart: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = android.net.Uri.parse("alarum://fire/$alarmId${if (snoozed) "/snoozed" else ""}")
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_STARTED_AT, intendedStart)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode() + if (snoozed) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The soonest upcoming alarm across all of them, for the UI and for HA. */
    fun nextAcross(): Pair<Alarm, Schedule.Ring>? =
        repository.alarms.value
            .mapNotNull { a -> nextRing(a)?.let { a to it } }
            .minByOrNull { it.second.startsAt }

    fun publishNextAlarm() {
        scope.launch {
            val next = nextAcross()
            val dismissed = repository.settings.value.lastDismissedEpochSec
            publisher.publish(
                AlarumState(
                    nextAlarm = next?.second?.startsAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    awakeBy = next?.second?.awakeBy?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    nextAlarmLabel = next?.first?.label?.ifBlank { "Alarm" },
                    ringing = "OFF",
                    stage = "idle",
                    stageSlug = "idle",
                    stageIndex = -1,
                    lastDismissed = dismissed?.let {
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault())
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    },
                ),
            )
        }
    }

    private companion object {
        const val TAG = "AlarumScheduler"
    }
}
