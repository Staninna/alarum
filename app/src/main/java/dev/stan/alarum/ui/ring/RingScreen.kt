package dev.stan.alarum.ui.ring

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stan.alarum.alarm.RingUiState
import dev.stan.alarum.domain.Challenge
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun RingScreen(
    state: RingUiState,
    shakeCount: Int,
    scannedTag: String?,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onResetShakes: () -> Unit,
) {
    // The room warms and then reddens as the ramp climbs, so the screen itself
    // tells you how much trouble you are in.
    val heat = (state.stageIndex.toFloat() / (state.totalStages - 1).coerceAtLeast(1))
        .coerceIn(0f, 1f)
    val background by animateColorAsState(
        lerp(Color(0xFF11100D), Color(0xFF3A0B08), heat),
        label = "background",
    )
    val accent = lerp(Color(0xFFFFC46B), Color(0xFFFF5449), heat)

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .padding(28.dp),
    ) {
        Column(Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            Spacer(Modifier.height(28.dp))
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 84.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Text(
                state.alarmLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(28.dp))
            StageStrip(state, accent)
            Spacer(Modifier.height(10.dp))
            Text(
                buildString {
                    append(state.stageName)
                    if (state.isFinalStage) {
                        append(" · this is as bad as it gets")
                    } else {
                        state.secondsToNextStage?.let { append(" · escalates in ${it}s") }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChallengeUi(
                challenge = state.challenge,
                accent = accent,
                shakeCount = shakeCount,
                scannedTag = scannedTag,
                onSolved = onDismiss,
                onResetShakes = onResetShakes,
            )
            if (state.allowSnooze) {
                TextButton(onClick = onSnooze) {
                    Text(
                        "Snooze ${state.snoozeMinutes} min",
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            } else {
                Text(
                    "No snoozing at this stage",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StageStrip(state: RingUiState, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(state.totalStages) { i ->
            val done = i < state.stageIndex
            val current = i == state.stageIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        when {
                            done -> accent.copy(alpha = 0.55f)
                            current -> accent
                            else -> Color.White.copy(alpha = 0.15f)
                        },
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
private fun ChallengeUi(
    challenge: Challenge,
    accent: Color,
    shakeCount: Int,
    scannedTag: String?,
    onSolved: () -> Unit,
    onResetShakes: () -> Unit,
) {
    when (challenge) {
        is Challenge.Tap -> BigButton("Turn off", accent, onSolved)

        is Challenge.LongPress -> LongPressButton(challenge.holdSeconds, accent, onSolved)

        is Challenge.Math -> MathChallenge(challenge, accent, onSolved)

        is Challenge.Shake -> {
            LaunchedEffect(challenge) { onResetShakes() }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Shake it off",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$shakeCount / ${challenge.requiredShakes}",
                    style = MaterialTheme.typography.displaySmall,
                    color = accent,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { shakeCount.toFloat() / challenge.requiredShakes },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = accent,
                )
                if (shakeCount >= challenge.requiredShakes) {
                    LaunchedEffect(Unit) { onSolved() }
                }
            }
        }

        is Challenge.Nfc -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!challenge.enrolled) {
                // Should be unreachable: the editor refuses to select NFC
                // without a tag. If it ever happens, an unopenable alarm is far
                // worse than a fallback.
                Text(
                    "No tag enrolled — falling back to a long press",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                LongPressButton(3, accent, onSolved)
            } else {
                Text(
                    "Go and scan the tag",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "It is not next to the bed. That was the point.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
                if (scannedTag != null && scannedTag == challenge.tagId) {
                    LaunchedEffect(scannedTag) { onSolved() }
                } else if (scannedTag != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Wrong tag", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun LongPressButton(holdSeconds: Int, accent: Color, onDone: () -> Unit) {
    var held by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(held) {
        if (!held) {
            progress = 0f
            return@LaunchedEffect
        }
        val steps = holdSeconds * 20
        repeat(steps) {
            delay(50)
            progress = (it + 1f) / steps
        }
        onDone()
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = accent.copy(alpha = if (held) 1f else 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Box(
                Modifier.fillMaxSize().pointerHold(
                    onStart = { held = true },
                    onEnd = { held = false },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (held) "Keep holding…" else "Hold for ${holdSeconds}s",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                )
            }
        }
        if (held) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = accent,
            )
        }
    }
}

@Composable
private fun MathChallenge(challenge: Challenge.Math, accent: Color, onSolved: () -> Unit) {
    var index by remember(challenge) { mutableStateOf(0) }
    var entry by remember(challenge) { mutableStateOf("") }
    var wrong by remember(challenge) { mutableStateOf(false) }

    val question = challenge.questions.getOrNull(index) ?: run {
        LaunchedEffect(Unit) { onSolved() }
        return
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (challenge.questions.size > 1) {
            Text(
                "Question ${index + 1} of ${challenge.questions.size}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Text(
            question.prompt,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = entry,
            onValueChange = { v ->
                entry = v.filter { it.isDigit() || it == '-' }.take(6)
                wrong = false
                if (entry.toIntOrNull() == question.answer) {
                    if (index == challenge.questions.lastIndex) onSolved()
                    else {
                        index += 1
                        entry = ""
                    }
                }
            },
            isError = wrong,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Press-and-hold gesture without dragging in a whole gesture library. */
private fun Modifier.pointerHold(onStart: () -> Unit, onEnd: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                onStart()
                tryAwaitRelease()
                onEnd()
            },
        )
    }
