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
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 1),
                allowSnooze = true,
            ),
            Stage(
                id = "stage-rising",
                name = "Rising",
                durationSec = 4 * 60,
                audio = AudioSpec(sound = Sounds.WARM_PAD, startLevel = 0.15f, endLevel = 0.40f),
                haptics = HapticSpec(VibePattern.SOFT_PULSE, amplitude = 90),
                flash = FlashSpec(screenBrightness = 0.35f),
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 2),
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
                speech = SpeechSpec(enabled = true, lines = Lines.firm, everySec = 60),
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
                    everySec = 30,
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
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 1),
            ),
            Stage(
                id = "s2", name = "Awake", durationSec = 0,
                audio = AudioSpec(sound = Sounds.SOFT_CHIME, startLevel = 0.35f, endLevel = 0.6f),
                haptics = HapticSpec(VibePattern.SOFT_PULSE),
                dismissal = DismissalSpec(DismissalMethod.MATH, difficulty = 1),
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
                speech = SpeechSpec(enabled = true, lines = Lines.firm, everySec = 30),
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
                    everySec = 25,
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
            "Good morning. It is time to get up, and the day is not going to start itself.",
            "This is your alarm, doing exactly the thing you asked it to do at the time.",
            "The day has started. You are very welcome to join it whenever it suits you.",
            "Up you get. Gently, for now, while gently is still very much on the table.",
            "Morning. There is no pressure yet, but I should mention that pressure is scheduled.",
            "Time to be awake. Everything after this is considerably easier if you start now.",
            "Here we are again, at the beginning of a day that has your name written on it.",
            "Good morning. This is the nicest I am going to sound for the rest of the morning.",
            "The lights are coming up. You may as well come up along with them.",
            "It is morning, and you did specifically ask to be told when that happened.",
        )

        val firm = listOf(
            "Right. It has been a few minutes now, and you are still exactly where you were.",
            "No rush at all. The rest of the world has apparently agreed to wait for you.",
            "This is the polite stage. I would make the most of it, because it does not last.",
            "Feet on the floor. That is the entire request. Two feet, one floor, and we are done.",
            "The day started without you. It seemed rude to keep it waiting any longer.",
            "I can go on like this all morning, and unlike you, I fully intend to.",
            "You did ask for this. Specifically. In a settings screen. At some considerable length.",
            "I am being reasonable about this for now, and that is a strictly temporary arrangement.",
            "This would be an excellent moment to get up, while getting up is still pleasant.",
            "Just so you know where we are in the morning: this is the calm part of it.",
            "I have several more things to say and I would genuinely rather not have to say them.",
            "There is a version of this morning where you get up now and it is fine. We are still in it.",
            "Nothing is on fire. You are simply late, and becoming later while we discuss it.",
            "Do consider this a courtesy. I should warn you that the courtesy runs out shortly.",
        )

        val blunt = listOf(
            "You set this alarm. You picked the time, you picked the profile, and now you are lying there pretending this is happening to you.",
            "Still there, then. Genuinely impressive commitment to doing absolutely nothing at all.",
            "Every single minute of this is a minute you are choosing, over and over, deliberately.",
            "The lights are on, the siren is going, and you are somehow still horizontal. Remarkable, really.",
            "This does not stop. You know it does not stop. And yet here we both are again.",
            "Be honest with yourself. You were never going back to sleep. You are just lying there losing.",
            "Everyone else managed to get up today. It is not a special talent, but well done them.",
            "I will keep saying things until you move, and I have considerably more patience than you have willpower.",
            "You are going to get up eventually, later than you wanted, in a worse mood. We both know how this ends.",
            "Whatever you had planned for this morning is smaller now. It gets a little smaller every minute you stay there.",
            "I am not going to pretend to be surprised. This is precisely what you did yesterday.",
            "The version of you that set this alarm had plans. He is clearly not the one making decisions right now.",
            "There is no version of this where you win. There is only the question of how long you would like it to take.",
            "You could have been up eight minutes ago and none of this would be happening. Just so we are clear on the sequence of events.",
            "Nothing about your situation improves while you lie there. That is not an opinion, it is arithmetic.",
            "I would ask whether you are awake, but we both know you have been awake for quite a while now.",
            "Lying very still does not make this stop. It has never once made this stop.",
            "You are not resting. You are negotiating with an alarm clock, and you are losing the negotiation.",
            "Every morning you do this, and every morning you seem surprised that it does not work.",
            "I can do this indefinitely. It is, genuinely, the only thing I was made for.",
            "The day is going to happen whether or not you attend. Attendance is merely strongly recommended.",
            "At this point you are actively choosing to be shouted at, which is certainly a choice.",
            "You have had ample warning. Several distinct stages of warning, all of which you ignored.",
            "This is going to be a worse morning than it needed to be, and that was entirely avoidable.",
            "Sit up. Then stand up. Then the noise stops. It is not a complicated sequence of events.",
            "I notice that you are still in bed. I will keep noticing, out loud, for as long as it takes.",
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
