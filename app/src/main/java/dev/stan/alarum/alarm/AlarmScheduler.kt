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
        val next = Schedule.nextOccurrence(alarm, ZonedDateTime.now()) ?: return
        val at = next.toInstant().toEpochMilli()
        scheduleAt(alarm.id, at)
        Log.i(TAG, "scheduled ${alarm.id} for $next")
    }

    fun snooze(alarmId: String, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        scheduleAt(alarmId, at, snoozed = true)
    }

    private fun scheduleAt(alarmId: String, epochMillis: Long, snoozed: Boolean = false) {
        val show = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            Intent(context, dev.stan.alarum.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(epochMillis, show), firePending(alarmId, snoozed))
    }

    fun cancel(alarmId: String) {
        am.cancel(firePending(alarmId, false))
        am.cancel(firePending(alarmId, true))
    }

    private fun firePending(alarmId: String, snoozed: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = android.net.Uri.parse("alarum://fire/$alarmId${if (snoozed) "/snoozed" else ""}")
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode() + if (snoozed) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The soonest upcoming alarm across all of them, for the UI and for HA. */
    fun nextAcross(): Pair<Alarm, ZonedDateTime>? {
        val now = ZonedDateTime.now()
        return repository.alarms.value
            .mapNotNull { a -> Schedule.nextOccurrence(a, now)?.let { a to it } }
            .minByOrNull { it.second }
    }

    fun publishNextAlarm() {
        scope.launch {
            val next = nextAcross()
            val dismissed = repository.settings.value.lastDismissedEpochSec
            publisher.publish(
                AlarumState(
                    nextAlarm = next?.second?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
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
