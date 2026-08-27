package dev.stan.alarum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {

    @Test
    fun `maths gets harder and longer with difficulty`() {
        val easy = Challenge.of(DismissalSpec(DismissalMethod.MATH, 1), null, 1) as Challenge.Math
        val hard = Challenge.of(DismissalSpec(DismissalMethod.MATH, 5), null, 1) as Challenge.Math
        assertEquals(1, easy.questions.size)
        assertEquals(3, hard.questions.size)
    }

    @Test
    fun `maths questions are internally consistent`() {
        (1..5).forEach { d ->
            val c = Challenge.of(DismissalSpec(DismissalMethod.MATH, d), null, 42) as Challenge.Math
            c.questions.forEach { q ->
                val expected = evaluate(q.prompt)
                assertEquals("difficulty $d: ${q.prompt}", expected, q.answer)
            }
        }
    }

    @Test
    fun `the same seed gives the same challenge`() {
        val a = Challenge.of(DismissalSpec(DismissalMethod.MATH, 3), null, 7) as Challenge.Math
        val b = Challenge.of(DismissalSpec(DismissalMethod.MATH, 3), null, 7) as Challenge.Math
        assertEquals(a.questions.map { it.prompt }, b.questions.map { it.prompt })
    }

    @Test
    fun `different ring seeds produce different maths questions`() {
        val spec = DismissalSpec(DismissalMethod.MATH, 3)
        val prompts = (0 until 50).map { seed ->
            (Challenge.of(spec, null, seed.toLong()) as Challenge.Math).questions.map { it.prompt }
        }.toSet()

        assertTrue("50 ring seeds produced only ${prompts.size} question sets", prompts.size > 40)
    }

    @Test
    fun `nfc without an enrolled tag reports itself as unusable`() {
        val none = Challenge.of(DismissalSpec(DismissalMethod.NFC), null, 1) as Challenge.Nfc
        assertFalse(none.enrolled)
        val some = Challenge.of(DismissalSpec(DismissalMethod.NFC), "04A2BC", 1) as Challenge.Nfc
        assertTrue(some.enrolled)
    }

    @Test
    fun `difficulty outside the range is clamped rather than throwing`() {
        val c = Challenge.of(DismissalSpec(DismissalMethod.SHAKE, 99), null, 1) as Challenge.Shake
        assertEquals(40, c.requiredShakes)
    }

    private fun evaluate(prompt: String): Int {
        val mul = Regex("""(\d+) x (\d+) \+ (\d+)""").find(prompt)
        if (mul != null) {
            val (a, b, c) = mul.destructured
            return a.toInt() * b.toInt() + c.toInt()
        }
        val two = Regex("""(\d+) ([+\-x]) (\d+)""").find(prompt)!!
        val (a, op, b) = two.destructured
        return when (op) {
            "+" -> a.toInt() + b.toInt()
            "-" -> a.toInt() - b.toInt()
            else -> a.toInt() * b.toInt()
        }
    }

    @Test
    fun `the easy dismissals are retired, but still parse`() {
        // Deleting them would fail the parse of any saved profile naming one,
        // and JsonStore turns a failed parse into "here are the defaults".
        assertTrue(DismissalMethod.entries.contains(DismissalMethod.TAP))
        assertFalse(DismissalMethod.TAP.selectable)
        assertFalse(DismissalMethod.LONG_PRESS.selectable)
        assertTrue(DismissalMethod.MATH.selectable)
        assertTrue(DismissalMethod.SHAKE.selectable)
        assertTrue(DismissalMethod.NFC.selectable)
    }

    @Test
    fun `hardening moves a stage off one-touch and up to the floor`() {
        assertEquals(
            DismissalSpec(DismissalMethod.MATH, DismissalSpec.MIN_DIFFICULTY),
            DismissalSpec(DismissalMethod.TAP, 1).hardened(),
        )
        assertEquals(
            DismissalSpec(DismissalMethod.MATH, DismissalSpec.MIN_DIFFICULTY),
            DismissalSpec(DismissalMethod.LONG_PRESS, 1).hardened(),
        )
        // Already hard enough is left exactly where it is.
        assertEquals(
            DismissalSpec(DismissalMethod.MATH, 5),
            DismissalSpec(DismissalMethod.LONG_PRESS, 5).hardened(),
        )
        val shake = DismissalSpec(DismissalMethod.SHAKE, 4)
        assertEquals(shake, shake.hardened())
        // The method survives even when only the difficulty was too low.
        assertEquals(
            DismissalSpec(DismissalMethod.SHAKE, DismissalSpec.MIN_DIFFICULTY),
            DismissalSpec(DismissalMethod.SHAKE, 1).hardened(),
        )
    }

    @Test
    fun `nothing shipped is dismissible below the floor`() {
        Defaults.all().flatMap { it.stages }.forEach { stage ->
            assertTrue(
                "${stage.name} is only difficulty ${stage.dismissal.difficulty}",
                stage.dismissal.difficulty >= DismissalSpec.MIN_DIFFICULTY,
            )
        }
    }

    @Test
    fun `the default spec is already hard`() {
        assertEquals(DismissalSpec(), DismissalSpec().hardened())
        assertTrue(DismissalSpec().difficulty >= DismissalSpec.MIN_DIFFICULTY)
    }

    @Test
    fun `hardening is idempotent`() {
        val once = DismissalSpec(DismissalMethod.TAP, 1).hardened()
        assertEquals(once, once.hardened())
    }

    @Test
    fun `no shipped stage can be killed with one thumb`() {
        Defaults.all().flatMap { it.stages }.forEach { stage ->
            assertTrue(
                "${stage.name} still uses ${stage.dismissal.method}",
                stage.dismissal.method.selectable,
            )
        }
    }
}
