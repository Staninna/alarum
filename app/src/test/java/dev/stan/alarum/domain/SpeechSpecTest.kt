package dev.stan.alarum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSpecTest {

    private val three = SpeechSpec(
        enabled = true,
        lines = listOf("Get up.", "Still there.", "Up. Now."),
    )

    @Test
    fun `silent until it is both enabled and has something to say`() {
        assertFalse(SpeechSpec().active)
        assertFalse(SpeechSpec(enabled = true).active)
        assertFalse(SpeechSpec(enabled = false, lines = listOf("hi")).active)
        assertTrue(three.active)
    }

    @Test
    fun `blank lines do not count`() {
        val spec = SpeechSpec(enabled = true, lines = listOf("", "   ", "\n"))
        assertFalse(spec.active)
        assertNull(spec.lineAt(0, 1L))
    }

    @Test
    fun `in order, it cycles`() {
        assertEquals("Get up.", three.lineAt(0, 1L))
        assertEquals("Still there.", three.lineAt(1, 1L))
        assertEquals("Up. Now.", three.lineAt(2, 1L))
        assertEquals("Get up.", three.lineAt(3, 1L))
        assertEquals("Still there.", three.lineAt(7, 1L))
    }

    @Test
    fun `shuffled, every line is heard before any repeats`() {
        val spec = three.copy(shuffle = true)
        val firstPass = (0..2).map { spec.lineAt(it, 99L) }
        assertEquals(spec.usableLines.toSet(), firstPass.toSet())

        val secondPass = (3..5).map { spec.lineAt(it, 99L) }
        assertEquals(spec.usableLines.toSet(), secondPass.toSet())
    }

    @Test
    fun `the same seed gives the same order, so a rotation does not reshuffle`() {
        val spec = three.copy(shuffle = true)
        assertEquals(
            (0..5).map { spec.lineAt(it, 42L) },
            (0..5).map { spec.lineAt(it, 42L) },
        )
    }

    @Test
    fun `different seeds give different orders`() {
        // The bug this pins: seeding from something constant per alarm gave a
        // shuffle that was identical every morning, which is a sequence you can
        // learn and therefore sleep through.
        val spec = SpeechSpec(
            enabled = true,
            shuffle = true,
            lines = (1..10).map { "line $it" },
        )
        val orders = (0 until 50).map { seed ->
            (0 until 10).map { spec.lineAt(it, seed * 1000L) }
        }.toSet()
        assertTrue("50 seeds produced only ${orders.size} distinct orders", orders.size > 40)
    }

    @Test
    fun `one seed is one order, however often it is asked`() {
        val spec = three.copy(shuffle = true)
        repeat(5) {
            assertEquals(
                listOf("Get up.", "Still there.", "Up. Now.").size,
                (0..2).map { i -> spec.lineAt(i, 777L) }.distinct().size,
            )
        }
    }

    @Test
    fun `a single line is not shuffled into oblivion`() {
        val one = SpeechSpec(enabled = true, lines = listOf("Up."), shuffle = true)
        assertEquals("Up.", one.lineAt(0, 5L))
        assertEquals("Up.", one.lineAt(9, 5L))
    }

    @Test
    fun `a negative index still lands on a real line`() {
        assertEquals("Up. Now.", three.lineAt(-1, 1L))
        assertEquals("Get up.", three.lineAt(-3, 1L))
    }

    @Test
    fun `the sharp end of the shipped profiles talks, and the gentle one does not`() {
        val brutal = Defaults.gentleThenBrutal().stages
        assertFalse("Gentle should not be shouting", brutal[0].speech.active)
        assertTrue("Insistent should talk", brutal[2].speech.active)
        assertTrue("Hostile should talk", brutal.last().speech.active)
        assertTrue("Hostile should shuffle", brutal.last().speech.shuffle)

        // The whole point of this one is that it does not shout at you.
        assertTrue(Defaults.sunriseOnly().stages.none { it.speech.active })

        assertTrue(Defaults.noMessing().stages.all { it.speech.active })
    }

    @Test
    fun `a talking stage has enough lines to not become wallpaper`() {
        Defaults.all().flatMap { it.stages }.filter { it.speech.active }.forEach { stage ->
            assertTrue(
                "${stage.name} repeats too soon",
                stage.speech.usableLines.size >= 4,
            )
        }
    }

    @Test
    fun `shipped lines are sentences rather than barks`() {
        // A three-word bark is a jingle, and a jingle stops landing. These are
        // meant to be sentences you have to listen to.
        Defaults.Lines.all.forEach { line ->
            assertTrue("too short to sting: \"$line\"", line.length >= 60)
        }
    }

    @Test
    fun `a stage leaves time for the house to finish saying a line`() {
        // The gap is measured publish to publish, so it has to outlast the
        // longest sentence in the stage or the speaker talks over itself.
        Defaults.all().flatMap { it.stages }.filter { it.speech.active }.forEach { stage ->
            val longest = stage.speech.usableLines.maxOf { it.length }
            // Roughly fifteen characters a second, which is a slow speaking voice.
            val secondsToSay = longest / 15
            assertTrue(
                "${stage.name} says a ${longest}-char line every ${stage.speech.everySec}s",
                stage.speech.everySec > secondsToSay,
            )
        }
    }

    /** What RingService does: one seed for the ring, an index that counts up. */
    private fun runStage(stage: Stage, utterances: Int, ringStartedAt: Long): List<String> =
        (0 until utterances).mapNotNull {
            stage.speech.lineAt(it, ringStartedAt + stage.name.hashCode())
        }

    @Test
    fun `a whole hostile stage never repeats itself`() {
        val hostile = Defaults.gentleThenBrutal().stages.last()
        val pool = hostile.speech.usableLines.size
        val said = runStage(hostile, pool, ringStartedAt = 1_700_000_000_000L)
        assertEquals("said $pool lines", pool, said.size)
        assertEquals("but only ${said.distinct().size} were different", pool, said.distinct().size)
    }

    @Test
    fun `two mornings do not sound the same`() {
        val hostile = Defaults.gentleThenBrutal().stages.last()
        val monday = runStage(hostile, 8, ringStartedAt = 1_700_000_000_000L)
        val tuesday = runStage(hostile, 8, ringStartedAt = 1_700_086_400_000L)
        assertNotEquals(monday, tuesday)
    }

    @Test
    fun `every talking stage shipped is shuffled`() {
        // A fixed order is a fixed order however many lines are in it: learn the
        // sequence once and it stops being something you have to listen to.
        Defaults.all().flatMap { it.stages }.filter { it.speech.active }.forEach { stage ->
            assertTrue("${stage.name} plays its lines in a fixed order", stage.speech.shuffle)
        }
    }

    @Test
    fun `pressing the test button repeatedly walks the list`() {
        // The bug: it asked for index 0 every time, so a stage with twenty-six
        // things to say demonstrated exactly one of them.
        val hostile = Defaults.gentleThenBrutal().stages.last()
        val presses = (0 until 6).map { hostile.speech.lineAt(it, 12345L) }
        assertEquals(6, presses.distinct().size)
    }

    @Test
    fun `the shipped suggestions are all sayable`() {
        assertTrue(Defaults.Lines.all.isNotEmpty())
        assertTrue(Defaults.Lines.all.all { it.isNotBlank() })
        assertEquals(Defaults.Lines.all.size, Defaults.Lines.all.distinct().size)
    }
}
