package dev.stan.alarum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationEngineTest {

    private val profile = EscalationProfile(
        id = "p", name = "test",
        stages = listOf(
            Stage("a", "Gentle", 60, AudioSpec(startLevel = 0f, endLevel = 0.2f)),
            Stage("b", "Rising", 120, AudioSpec(startLevel = 0.2f, endLevel = 0.6f)),
            Stage("c", "Hostile", 0, AudioSpec(startLevel = 1f, endLevel = 1f)),
        ),
    )
    private val engine = EscalationEngine(profile)

    @Test
    fun `starts in the first stage`() {
        val s = engine.stateAt(0)
        assertEquals(0, s.stageIndex)
        assertEquals(0f, s.audioLevel, 0.001f)
    }

    @Test
    fun `ramps linearly across a stage`() {
        assertEquals(0.1f, engine.stateAt(30).audioLevel, 0.01f)
        assertEquals(0.2f, engine.stateAt(59).audioLevel, 0.02f)
    }

    @Test
    fun `crosses into the next stage at the boundary`() {
        assertEquals(0, engine.stateAt(59).stageIndex)
        assertEquals(1, engine.stateAt(60).stageIndex)
        assertEquals(1, engine.stateAt(179).stageIndex)
        assertEquals(2, engine.stateAt(180).stageIndex)
    }

    @Test
    fun `final stage sustains forever`() {
        val hour = engine.stateAt(3600)
        assertEquals(2, hour.stageIndex)
        assertTrue(hour.isFinalStage)
        assertEquals(1f, hour.audioLevel, 0.001f)

        val day = engine.stateAt(86_400)
        assertEquals(2, day.stageIndex)
    }

    @Test
    fun `negative elapsed is treated as the very start`() {
        assertEquals(0, engine.stateAt(-5).stageIndex)
    }

    @Test
    fun `ramp length excludes the unbounded final stage`() {
        assertEquals(180, profile.rampSec)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a profile with no stages is rejected`() {
        EscalationEngine(EscalationProfile("x", "empty", emptyList()))
    }

    @Test
    fun `shipped defaults are all playable`() {
        Defaults.all().forEach { p ->
            val e = EscalationEngine(p)
            // Whatever the profile, an hour in it must still be doing something.
            val late = e.stateAt(3600)
            assertTrue("${p.name} goes silent", late.stage.audio.enabled)
            assertTrue("${p.name} is not at full tilt", late.audioLevel > 0.3f)
        }
    }

    @Test
    fun `zero-duration middle stage does not trap the ramp`() {
        val odd = EscalationProfile(
            "o", "odd",
            listOf(
                Stage("1", "one", 0),
                Stage("2", "two", 30),
                Stage("3", "three", 0),
            ),
        )
        val e = EscalationEngine(odd)
        assertEquals(1, e.stateAt(0).stageIndex)
        assertEquals(2, e.stateAt(30).stageIndex)
    }
}
