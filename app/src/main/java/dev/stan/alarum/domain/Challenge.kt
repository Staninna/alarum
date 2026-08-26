package dev.stan.alarum.domain

import kotlin.random.Random

/**
 * The thing standing between you and silence.
 *
 * Generation is pure and seeded so the difficulty curve can be tested, and so a
 * challenge survives a configuration change without turning into a different
 * sum halfway through.
 */
sealed interface Challenge {

    data object Tap : Challenge

    data class LongPress(val holdSeconds: Int) : Challenge

    data class Math(val questions: List<Question>) : Challenge {
        data class Question(val prompt: String, val answer: Int)
    }

    data class Shake(val requiredShakes: Int) : Challenge

    data class Nfc(val tagId: String?) : Challenge {
        /** Without an enrolled tag this challenge cannot be satisfied. */
        val enrolled: Boolean get() = !tagId.isNullOrBlank()
    }

    companion object {
        fun of(spec: DismissalSpec, enrolledTagId: String?, seed: Long): Challenge {
            val d = spec.difficulty.coerceIn(1, 5)
            return when (spec.method) {
                DismissalMethod.TAP -> Tap
                DismissalMethod.LONG_PRESS -> LongPress(holdSeconds = d)
                DismissalMethod.MATH -> Math(mathQuestions(d, seed))
                DismissalMethod.SHAKE -> Shake(requiredShakes = d * 8)
                DismissalMethod.NFC -> Nfc(enrolledTagId)
            }
        }

        /**
         * Difficulty 1 is one two-digit sum. Difficulty 5 is three questions
         * with multiplication in them, which is about the limit of what is fair
         * to ask of someone who has been awake for eleven seconds.
         */
        private fun mathQuestions(difficulty: Int, seed: Long): List<Math.Question> {
            val rng = Random(seed)
            val count = when (difficulty) {
                1, 2 -> 1
                3, 4 -> 2
                else -> 3
            }
            return (0 until count).map { i ->
                val r = Random(seed + i * 7919L)
                when (difficulty) {
                    1 -> {
                        val a = r.nextInt(2, 20); val b = r.nextInt(2, 20)
                        Math.Question("$a + $b", a + b)
                    }
                    2 -> {
                        val a = r.nextInt(10, 60); val b = r.nextInt(10, 40)
                        Math.Question("$a + $b", a + b)
                    }
                    3 -> {
                        val a = r.nextInt(20, 99); val b = r.nextInt(10, 60)
                        if (r.nextBoolean()) Math.Question("$a - $b", a - b)
                        else Math.Question("$a + $b", a + b)
                    }
                    4 -> {
                        val a = r.nextInt(3, 13); val b = r.nextInt(3, 13)
                        Math.Question("$a x $b", a * b)
                    }
                    else -> {
                        val a = r.nextInt(6, 19); val b = r.nextInt(4, 14)
                        val c = r.nextInt(2, 30)
                        Math.Question("$a x $b + $c", a * b + c)
                    }
                }
            }.also { rng.nextInt() }
        }
    }
}
