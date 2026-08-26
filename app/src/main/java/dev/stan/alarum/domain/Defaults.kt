package dev.stan.alarum.domain

/**
 * Seed profiles. Deliberately free of any entity id or house-specific detail —
 * what the house does lives in Home Assistant automations keyed off the stage
 * this app publishes. These only describe what the phone itself does.
 */
object Defaults {

    const val GENTLE_ID = "profile-gentle"

    fun gentleThenBrutal() = EscalationProfile(
        id = GENTLE_ID,
        name = "Gentle, then not",
        stages = listOf(
            Stage(
                id = "stage-gentle",
                name = "Gentle",
                durationSec = 5 * 60,
                audio = AudioSpec(sound = Sounds.SOFT_CHIME, startLevel = 0f, endLevel = 0.15f),
                haptics = HapticSpec(VibePattern.NONE),
                flash = FlashSpec(),
                dismissal = DismissalSpec(DismissalMethod.TAP),
                allowSnooze = true,
            ),
            Stage(
                id = "stage-rising",
                name = "Rising",
                durationSec = 4 * 60,
                audio = AudioSpec(sound = Sounds.WARM_PAD, startLevel = 0.15f, endLevel = 0.40f),
                haptics = HapticSpec(VibePattern.SOFT_PULSE, amplitude = 90),
                flash = FlashSpec(screenBrightness = 0.35f),
                dismissal = DismissalSpec(DismissalMethod.LONG_PRESS, difficulty = 2),
                allowSnooze = true,
            ),
            Stage(
                id = "stage-insistent",
                name = "Insistent",
                durationSec = 4 * 60,
                audio = AudioSpec(sound = Sounds.PULSE_TONE, startLevel = 0.40f, endLevel = 0.80f),
                haptics = HapticSpec(VibePattern.PULSE, amplitude = 170),
                flash = FlashSpec(screenBrightness = 0.75f),
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 2),
                allowSnooze = true,
            ),
            Stage(
                id = "stage-hostile",
                name = "Hostile",
                durationSec = 0, // final stage: sustains until dismissed
                audio = AudioSpec(
                    sound = Sounds.SIREN,
                    startLevel = 0.9f,
                    endLevel = 1f,
                    commandeerSystemVolume = true,
                ),
                haptics = HapticSpec(VibePattern.RELENTLESS, amplitude = 255),
                flash = FlashSpec(screenBrightness = 1f, torchHz = 4f),
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 4),
                allowSnooze = false,
            ),
        ),
    )

    fun sunriseOnly() = EscalationProfile(
        id = "profile-sunrise",
        name = "Sunrise only",
        stages = listOf(
            Stage(
                id = "s1", name = "Fade in", durationSec = 15 * 60,
                audio = AudioSpec(sound = Sounds.WARM_PAD, startLevel = 0f, endLevel = 0.35f),
                dismissal = DismissalSpec(DismissalMethod.TAP),
            ),
            Stage(
                id = "s2", name = "Awake", durationSec = 0,
                audio = AudioSpec(sound = Sounds.SOFT_CHIME, startLevel = 0.35f, endLevel = 0.6f),
                haptics = HapticSpec(VibePattern.SOFT_PULSE),
                dismissal = DismissalSpec(DismissalMethod.LONG_PRESS, difficulty = 1),
            ),
        ),
    )

    fun noMessing() = EscalationProfile(
        id = "profile-blunt",
        name = "No messing about",
        stages = listOf(
            Stage(
                id = "s1", name = "Up", durationSec = 60,
                audio = AudioSpec(sound = Sounds.HARSH_BEEP, startLevel = 0.6f, endLevel = 0.9f),
                haptics = HapticSpec(VibePattern.PULSE, amplitude = 200),
                flash = FlashSpec(screenBrightness = 1f),
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 3),
                allowSnooze = false,
            ),
            Stage(
                id = "s2", name = "Now", durationSec = 0,
                audio = AudioSpec(
                    sound = Sounds.SIREN, startLevel = 1f, endLevel = 1f,
                    commandeerSystemVolume = true,
                ),
                haptics = HapticSpec(VibePattern.RELENTLESS, amplitude = 255),
                flash = FlashSpec(screenBrightness = 1f, torchHz = 6f),
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 5),
                allowSnooze = false,
            ),
        ),
    )

    fun all() = listOf(gentleThenBrutal(), sunriseOnly(), noMessing())

    /**
     * Starter lines for the speech editor's suggest button.
     *
     * Split by temperament rather than offered as one pool: the whole point of
     * the app is that the first stage and the last one are not the same kind of
     * thing, and neither should what they say be.
     */
    object Lines {
        val kind = listOf(
            "Good morning.",
            "It is time to get up.",
            "The day has started. You are welcome to join it.",
            "Gently now. Up you get.",
        )

        val firm = listOf(
            "Get up.",
            "You are still in bed.",
            "This is the part where you sit up.",
            "Feet on the floor. That is all that is being asked.",
            "The longer this goes on, the worse it gets.",
        )

        val blunt = listOf(
            "Get out of bed.",
            "Still lying there, then.",
            "Everyone else managed it.",
            "You set this alarm. You did this to yourself.",
            "Every minute of this is a minute you chose.",
            "This will not stop. You know it will not stop.",
            "Be honest, you are not going back to sleep now.",
            "Up. Now.",
        )

        val all = kind + firm + blunt
    }

    fun newStage(index: Int) = Stage(
        id = "stage-${System.nanoTime()}",
        name = "Stage ${index + 1}",
        durationSec = 3 * 60,
        audio = AudioSpec(sound = Sounds.PULSE_TONE, startLevel = 0.3f, endLevel = 0.6f),
    )
}
