package dev.stan.alarum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTimelineTest {

    private val profile = EscalationProfile(
        id = "p", name = "test",
        stages = listOf(
            Stage("a", "Gentle", 60),
            Stage("b", "Rising", 120),
            Stage("c", "Hostile", 0),
        ),
    )
    private val line = PreviewTimeline(profile)

    @Test
    fun `the unbounded final stage gets a finite tail`() {
        assertEquals(180, line.rampSec)
        // A quarter of the ramp is 45s, under the one-minute floor.
        assertEquals(60, line.tailSec)
        assertEquals(240, line.totalSec)
    }

    @Test
    fun `a long ramp gets a proportionate tail`() {
        val long = PreviewTimeline(
            EscalationProfile(
                "l", "long",
                listOf(Stage("a", "Fade", 20 * 60), Stage("b", "Up", 0)),
            ),
        )
        assertEquals(5 * 60, long.tailSec)
    }

    @Test
    fun `a single-stage profile still has somewhere to scrub`() {
        val one = PreviewTimeline(
            EscalationProfile("o", "one", listOf(Stage("a", "Only", 0))),
        )
        assertEquals(0, one.rampSec)
        assertTrue(one.totalSec > 0)
        assertEquals(0, one.stateAt(one.totalSec).stageIndex)
    }

    @Test
    fun `scrubbing past the end clamps rather than running forever`() {
        assertEquals(line.totalSec, line.secondsAt(2f))
        assertEquals(1f, line.fractionOf(9999), 0.001f)
        assertEquals(2, line.stateAt(9999).stageIndex)
    }

    @Test
    fun `scrubbing before the start clamps to zero`() {
        assertEquals(0, line.secondsAt(-1f))
        assertEquals(0f, line.fractionOf(-30), 0.001f)
    }

    @Test
    fun `fraction and seconds round-trip`() {
        listOf(0, 30, 60, 179, 180, 240).forEach { sec ->
            assertEquals(sec, line.secondsAt(line.fractionOf(sec)))
        }
    }

    @Test
    fun `stage marks land where the stages actually begin`() {
        assertEquals(listOf(0f, 60f / 240, 180f / 240), line.stageMarks)
    }

    @Test
    fun `every shipped profile fits on a scrubber`() {
        Defaults.all().forEach { p ->
            val l = PreviewTimeline(p)
            assertTrue("${p.name} has no timeline", l.totalSec > 0)
            // The last stage must be reachable, or the previewer would never
            // show you the part you most want to hear.
            assertTrue(
                "${p.name} never reaches its final stage",
                l.stateAt(l.totalSec).isFinalStage,
            )
        }
    }
}
