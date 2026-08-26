package dev.stan.alarum.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, amsterdam)

    private fun alarm(hour: Int, minute: Int, days: Set<Int> = emptySet()) =
        Alarm(id = "a", hour = hour, minute = minute, days = days, profileId = "p")

    @Test
    fun `one-shot alarm later today`() {
        val now = at(2026, 8, 26, 6, 0)
        assertEquals(at(2026, 8, 26, 7, 0), Schedule.nextOccurrence(alarm(7, 0), now))
    }

    @Test
    fun `one-shot alarm rolls to tomorrow once the time has passed`() {
        val now = at(2026, 8, 26, 8, 0)
        assertEquals(at(2026, 8, 27, 7, 0), Schedule.nextOccurrence(alarm(7, 0), now))
    }

    @Test
    fun `repeating alarm whose only day is today but already past waits a week`() {
        // 2026-08-26 is a Wednesday.
        val now = at(2026, 8, 26, 8, 0)
        val wednesdayOnly = alarm(7, 0, setOf(3))
        assertEquals(at(2026, 9, 2, 7, 0), Schedule.nextOccurrence(wednesdayOnly, now))
    }

    @Test
    fun `repeating alarm today when the time is still ahead`() {
        val now = at(2026, 8, 26, 6, 0)
        assertEquals(at(2026, 8, 26, 7, 0), Schedule.nextOccurrence(alarm(7, 0, setOf(3)), now))
    }

    @Test
    fun `weekday alarm on a friday evening lands on monday`() {
        // 2026-08-28 is a Friday.
        val now = at(2026, 8, 28, 20, 0)
        assertEquals(
            at(2026, 8, 31, 7, 0),
            Schedule.nextOccurrence(alarm(7, 0, Schedule.weekdays), now),
        )
    }

    @Test
    fun `skip next burns exactly one occurrence`() {
        val now = at(2026, 8, 26, 6, 0)
        val skipped = alarm(7, 0, Schedule.everyDay).copy(skipNext = true)
        assertEquals(at(2026, 8, 27, 7, 0), Schedule.nextOccurrence(skipped, now))
    }

    @Test
    fun `a disabled alarm never fires`() {
        val now = at(2026, 8, 26, 6, 0)
        assertNull(Schedule.nextOccurrence(alarm(7, 0).copy(enabled = false), now))
    }

    @Test
    fun `day labels collapse to something readable`() {
        assertEquals("Weekdays", Schedule.daysLabel(Schedule.weekdays))
        assertEquals("Every day", Schedule.daysLabel(Schedule.everyDay))
        assertEquals("Once", Schedule.daysLabel(emptySet()))
        assertEquals("Mon Thu", Schedule.daysLabel(setOf(1, 4)))
    }
}
