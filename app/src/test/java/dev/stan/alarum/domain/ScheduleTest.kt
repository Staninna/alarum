package dev.stan.alarum.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // --- what the time on the alarm actually means ------------------------

    private val ramp = 13 * 60 // "Gentle, then not"

    @Test
    fun `by default the time you set is when it starts ringing`() {
        val now = at(2026, 8, 26, 6, 0)
        val ring = Schedule.nextRing(alarm(7, 0), now, ramp)!!
        assertEquals(at(2026, 8, 26, 7, 0), ring.startsAt)
        // And the worst of it arrives a whole ramp after the time on the face,
        // which is the thing that surprises people.
        assertEquals(at(2026, 8, 26, 7, 13), ring.awakeBy)
    }

    @Test
    fun `awake by moves the start earlier so the deadline lands on your time`() {
        val now = at(2026, 8, 26, 6, 0)
        val ring = Schedule.nextRing(alarm(7, 0).copy(awakeBy = true), now, ramp)!!
        assertEquals(at(2026, 8, 26, 6, 47), ring.startsAt)
        assertEquals(at(2026, 8, 26, 7, 0), ring.awakeBy)
    }

    @Test
    fun `set with less than a ramp to spare, it starts in the past`() {
        // 06:55 for a 07:00 deadline and a 13 minute ramp. The start is behind
        // us; the alarm fires at once and joins the ramp partway through rather
        // than pushing the deadline out to 07:08.
        val now = at(2026, 8, 26, 6, 55)
        val ring = Schedule.nextRing(alarm(7, 0).copy(awakeBy = true), now, ramp)!!
        assertTrue(ring.startsAt.isBefore(now))
        assertEquals(at(2026, 8, 26, 7, 0), ring.awakeBy)
    }

    @Test
    fun `a zero-length ramp makes both anchors identical`() {
        val now = at(2026, 8, 26, 6, 0)
        val plain = Schedule.nextRing(alarm(7, 0), now, 0)!!
        val deadline = Schedule.nextRing(alarm(7, 0).copy(awakeBy = true), now, 0)!!
        assertEquals(plain.startsAt, deadline.startsAt)
        assertEquals(plain.awakeBy, deadline.awakeBy)
    }

    @Test
    fun `awake by still respects days, skipping and being switched off`() {
        val now = at(2026, 8, 26, 8, 0) // Wednesday, past the time
        val a = alarm(7, 0, Schedule.weekdays).copy(awakeBy = true)
        assertEquals(at(2026, 8, 27, 6, 47), Schedule.nextRing(a, now, ramp)!!.startsAt)

        val skipped = a.copy(days = Schedule.everyDay, skipNext = true)
        assertEquals(at(2026, 8, 28, 6, 47), Schedule.nextRing(skipped, now, ramp)!!.startsAt)

        assertNull(Schedule.nextRing(a.copy(enabled = false), now, ramp))
    }
}
