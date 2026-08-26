package dev.stan.alarum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `the shipped suggestions are all sayable`() {
        assertTrue(Defaults.Lines.all.isNotEmpty())
        assertTrue(Defaults.Lines.all.all { it.isNotBlank() })
        assertEquals(Defaults.Lines.all.size, Defaults.Lines.all.distinct().size)
    }
}
