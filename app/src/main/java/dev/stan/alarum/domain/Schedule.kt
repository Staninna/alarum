package dev.stan.alarum.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Next-occurrence maths, kept away from Android so the awkward cases — DST,
 * a repeating alarm whose only day is today but the time has passed, skipping
 * tomorrow — can be tested directly.
 */
object Schedule {

    /**
     * A scheduled ring: when the noise starts, and when it has to have worked.
     *
     * Both are always populated, whichever way the alarm is anchored, because
     * the phone needs one and Home Assistant wants the other.
     */
    data class Ring(
        val startsAt: ZonedDateTime,
        val awakeBy: ZonedDateTime,
    )

    /**
     * Turn the time on the alarm into an actual ring.
     *
     * With [Alarm.awakeBy] set, the time you chose is the deadline rather than
     * the start, so the ring begins [rampSec] earlier and the final stage lands
     * on it. That start can be in the past — you set an alarm for ten minutes
     * from now with a thirteen minute ramp — and that is deliberate: it fires
     * at once and the ring joins the ramp partway through, because the deadline
     * is the thing you asked for and the ramp is only how it gets there.
     */
    fun nextRing(alarm: Alarm, now: ZonedDateTime, rampSec: Int): Ring? {
        val chosen = nextOccurrence(alarm, now) ?: return null
        val ramp = rampSec.coerceAtLeast(0).toLong()
        return if (alarm.awakeBy) {
            Ring(startsAt = chosen.minusSeconds(ramp), awakeBy = chosen)
        } else {
            Ring(startsAt = chosen, awakeBy = chosen.plusSeconds(ramp))
        }
    }

    /**
     * The next moment this alarm should fire, or null if it never will.
     *
     * A one-shot alarm fires at the next occurrence of its time. A repeating
     * alarm fires at the next matching weekday. [Alarm.skipNext] burns exactly
     * one occurrence.
     */
    fun nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        val first = firstAfter(alarm, now) ?: return null
        if (!alarm.skipNext) return first
        return firstAfter(alarm, first.plusMinutes(1))
    }

    private fun firstAfter(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        val zone = now.zone
        // 8 days of lookahead covers every weekday plus today twice, which is
        // what a repeating alarm whose day is today-but-already-past needs.
        for (offset in 0..8) {
            val date: LocalDate = now.toLocalDate().plusDays(offset.toLong())
            if (alarm.isRepeating && date.dayOfWeek.value !in alarm.days) continue
            val candidate = resolve(date, alarm.hour, alarm.minute, zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    /**
     * Turn a wall-clock time into an instant.
     *
     * On the spring-forward night the requested time may not exist. java.time
     * shifts it forward by the gap, which is the behaviour you want from an
     * alarm: 02:30 on a night with no 02:30 becomes 03:30 rather than silently
     * not ringing.
     */
    private fun resolve(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)), zone)

    fun dayLabel(iso: Int): String = when (DayOfWeek.of(iso)) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    val weekdays = setOf(1, 2, 3, 4, 5)
    val weekend = setOf(6, 7)
    val everyDay = setOf(1, 2, 3, 4, 5, 6, 7)

    fun daysLabel(days: Set<Int>): String = when {
        days.isEmpty() -> "Once"
        days == everyDay -> "Every day"
        days == weekdays -> "Weekdays"
        days == weekend -> "Weekends"
        else -> days.sorted().joinToString(" ") { dayLabel(it) }
    }
}
