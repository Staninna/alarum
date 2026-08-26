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
}
