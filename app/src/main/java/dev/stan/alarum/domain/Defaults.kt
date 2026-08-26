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
                // In order, because this stage is an argument that builds.
                speech = SpeechSpec(enabled = true, lines = Lines.firm, everySec = 45),
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
                // Shuffled and frequent. Predictability is what lets you tune
                // something out, which is the one thing this stage must not be.
                speech = SpeechSpec(
                    enabled = true,
                    lines = Lines.blunt,
                    everySec = 20,
                    shuffle = true,
                ),
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
                speech = SpeechSpec(enabled = true, lines = Lines.firm, everySec = 20),
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
                speech = SpeechSpec(
                    enabled = true,
                    lines = Lines.blunt,
                    everySec = 15,
                    shuffle = true,
                ),
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 5),
                allowSnooze = false,
            ),
        ),
    )

    fun all() = listOf(gentleThenBrutal(), sunriseOnly(), noMessing())

    /**
     * What the house says, by temperament.
     *
     * Split rather than offered as one pool because the whole thesis of the app
     * is that the first stage and the last one are not the same kind of thing,
     * and neither should what they say be. Doubles as the source for the
     * editor's suggest button, which works down the list as you press it.
     */
    object Lines {
        val kind = listOf(
            "Good morning.",
            "It is time to get up.",
            "The day has started. You are welcome to join it.",
            "Gently now. Up you get.",
        )

        val firm = listOf(
            "Right. Up.",
            "You are still horizontal.",
            "This is the polite stage. It does not last.",
            "Feet on the floor. That is the entire ask.",
            "The day started without you.",
        )

        val blunt = listOf(
            "Get up.",
            "Still lying there, then.",
            "You set this alarm. This is your own doing.",
            "Every minute of this was a choice.",
            "The lights are on, the siren is on, and you are still in bed.",
            "This does not stop. You know it does not stop.",
            "Be honest. You are not going back to sleep.",
            "Everyone else managed it.",
            "Pathetic effort so far.",
            "Up. Now. Move.",
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
