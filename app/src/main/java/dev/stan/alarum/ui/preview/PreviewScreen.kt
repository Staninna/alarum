package dev.stan.alarum.ui.preview

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.alarum.alarm.PreviewUiState
import dev.stan.alarum.domain.EscalationProfile
import dev.stan.alarum.domain.PreviewSpeed
import dev.stan.alarum.domain.PreviewTimeline
import dev.stan.alarum.domain.Sounds
import dev.stan.alarum.domain.VibePattern
import dev.stan.alarum.ui.AlarumViewModel
import dev.stan.alarum.ui.components.SectionCard
import dev.stan.alarum.ui.components.SwitchRow

/**
 * A profile, rehearsed.
 *
 * The whole point is to find out what a ramp actually feels like without
 * setting an alarm for six minutes' time and waiting. It runs the real
 * effectors on a clock you can pause, scrub and take up to 60×, so a
 * twenty-five minute wake-up is twenty-five seconds of listening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    vm: AlarumViewModel,
    profile: EscalationProfile,
    onDone: () -> Unit,
) {
    val session = vm.preview
    val state by session.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    // Defaults to publishing only when there is somewhere to publish to, so the
    // previewer is silent about Home Assistant on a phone that has none.
    val haConfigured = settings.ha.mqttConfigured || settings.ha.restConfigured

    DisposableEffect(profile) {
        session.start(profile, publishToHa = haConfigured)
        onDispose { session.stop() }
    }

    // Walking away from the previewer must not leave a siren going in your
    // pocket. Backgrounding pauses; coming back leaves it where you left it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.setPlaying(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ApplyScreenBrightness(state?.screenBrightness ?: 0f)

    val timeline = remember(profile) { PreviewTimeline(profile) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            // The last stage is meant to be intolerable, so the way out cannot
            // be a small arrow in the corner. Big, red, and always on top of
            // the siren.
            ExtendedFloatingActionButton(
                onClick = onDone,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                icon = { Icon(Icons.Default.Close, contentDescription = null) },
                text = { Text("Stop preview") },
            )
        },
    ) { padding ->
        val s = state
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s == null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Nothing to preview — an alarm is ringing for real, which wins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Hero(s)
                Transport(
                    state = s,
                    marks = timeline.stageMarks,
                    onSeek = session::seekTo,
                    onSeekStage = session::seekToStage,
                    onPlayPause = { session.setPlaying(!s.playing) },
                    onSpeed = session::setSpeed,
                    onMute = session::setMuted,
                    stageNames = profile.stages.map { it.name },
                )
                Phone(s)
                House(
                    s,
                    onPublish = session::setPublishing,
                    onDismiss = { session.dismiss(); onDone() },
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** The stage you are in, and how much worse it is about to get. */
@Composable
private fun Hero(s: PreviewUiState) {
    val heat = (s.stageIndex.toFloat() / (s.totalStages - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val background by animateColorAsState(
        lerp(Color(0xFF1A1814), Color(0xFF3A0B08), heat),
        label = "preview-heat",
    )
    val accent = lerp(Color(0xFFFFC46B), Color(0xFFFF5449), heat)

    Surface(shape = MaterialTheme.shapes.large, color = background, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Stage ${s.stageIndex + 1} of ${s.totalStages}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.55f),
            )
            Text(
                s.stageName,
                fontSize = 40.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    s.isFinalStage && s.inTail ->
                        "This is as bad as it gets, and it stays here until you deal with it"
                    s.secondsToNextStage != null -> "Escalates in ${s.secondsToNextStage}s"
                    else -> "Holding"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clock(s.elapsedSec),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    " into the alarm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(12.dp))
                if (s.muted) {
                    Text(
                        "muted",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            LevelMeter(s.audioLevel, accent)
        }
    }
}

/** How loud it is right now, drawn rather than described. */
@Composable
private fun LevelMeter(level: Float, accent: Color) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(5.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(level.coerceIn(0f, 1f))
                    .height(10.dp)
                    .background(accent, RoundedCornerShape(5.dp)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Volume ${"%.0f".format(level * 100)}%",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Transport(
    state: PreviewUiState,
    marks: List<Float>,
    stageNames: List<String>,
    onSeek: (Float) -> Unit,
    onSeekStage: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onSpeed: (PreviewSpeed) -> Unit,
    onMute: (Boolean) -> Unit,
) {
    // While dragging, the slider follows the finger rather than the clock,
    // otherwise the running preview yanks the thumb back every 50ms.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    SectionCard {
        StageBar(marks, state.stageIndex, state.fraction)
        Slider(
            value = if (dragging) dragValue else state.fraction,
            onValueChange = {
                dragging = true
                dragValue = it
                onSeek(it)
            },
            onValueChangeFinished = { dragging = false },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                clock(state.elapsedSec),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                clock(state.totalSec),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledIconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.filledIconButtonColors(),
                modifier = Modifier.size(52.dp),
            ) {
                when {
                    state.atEnd && !state.playing ->
                        Icon(Icons.Default.Refresh, contentDescription = "Play again")
                    state.playing -> PauseGlyph()
                    else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
            PreviewSpeed.entries.forEach { sp ->
                FilterChip(
                    selected = state.speed == sp,
                    onClick = { onSpeed(sp) },
                    label = { Text(sp.label) },
                )
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = state.muted,
                onClick = { onMute(!state.muted) },
                label = { Text(if (state.muted) "Muted" else "Mute") },
            )
        }

        Text(
            "Mute silences the tone and the vibration. The screen and torch keep going, because they are the half you can preview at a desk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.publishing && state.speed != PreviewSpeed.REAL) {
            Text(
                "Home Assistant runs in real time. At ${state.speed.label} a light with a long transition is restarted before it has got anywhere, so the house will look like it is doing nothing. Drop to 1× to preview the house, or turn publishing off to rehearse just the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()
        Text("Jump to", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stageNames.forEachIndexed { i, name ->
                FilterChip(
                    selected = i == state.stageIndex,
                    onClick = { onSeekStage(i) },
                    label = { Text(name, maxLines = 1) },
                )
            }
        }
    }
}

/**
 * The scrubber's ruler: one block per stage, sized by how much of the timeline
 * it owns, so you can see at a glance that "Gentle" is half the alarm.
 */
@Composable
private fun StageBar(marks: List<Float>, currentIndex: Int, fraction: Float) {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        val gap = 3f
        marks.forEachIndexed { i, start ->
            val end = marks.getOrNull(i + 1) ?: 1f
            val x = start * size.width
            val w = ((end - start) * size.width - gap).coerceAtLeast(2f)
            val done = i < currentIndex
            val current = i == currentIndex
            drawRoundRect(
                color = when {
                    current -> scheme.primary
                    done -> scheme.primary.copy(alpha = 0.4f)
                    else -> scheme.onSurface.copy(alpha = 0.12f)
                },
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
        // The playhead, so the position reads even when a stage is a sliver.
        drawRect(
            color = scheme.onSurface,
            topLeft = Offset((fraction * size.width - 1f).coerceIn(0f, size.width - 2f), 0f),
            size = Size(2f, size.height),
        )
    }
}

/** What the phone itself is doing at this instant. */
@Composable
private fun Phone(s: PreviewUiState) {
    SectionCard(title = "What the phone is doing") {
        Fact("Tone", Sounds.label(s.sound))
        Fact(
            "Vibration",
            if (s.haptics.pattern == VibePattern.NONE) "Off"
            else "${s.haptics.pattern.name.lowercase().replace('_', ' ')} at ${s.haptics.amplitude}/255",
        )
        Fact(
            "Screen",
            if (s.screenBrightness <= 0.01f) "Left alone" else "${"%.0f".format(s.screenBrightness * 100)}%",
        )
        Fact("Torch", if (s.torchHz < 0.2f) "Off" else "${"%.1f".format(s.torchHz)} Hz strobe")
        Fact(
            "To get rid of it",
            s.dismissal.method.label +
                if (s.dismissal.method != dev.stan.alarum.domain.DismissalMethod.TAP) {
                    " · difficulty ${s.dismissal.difficulty}/5"
                } else "",
        )
        Fact("Snooze", if (s.allowSnooze) "Allowed" else "Refused")
        Fact(
            "House says",
            if (!s.speech.active) "Nothing"
            else "${s.speech.usableLines.size} lines, every ${s.speech.everySec}s" +
                if (s.speech.shuffle) ", shuffled" else "",
        )
        if (s.speechNote != null) {
            Text(
                s.speechNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.commandeersVolume) {
            Text(
                "This stage takes over the system alarm volume when it fires for real. The preview does not, so what you are hearing is the app's own gain against your current volume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What Home Assistant is being told, and what it is not. */
@Composable
private fun House(
    s: PreviewUiState,
    onPublish: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SectionCard(title = "What the house sees") {
        SwitchRow(
            label = "Publish to Home Assistant",
            checked = s.publishing,
            onChange = onPublish,
            help = "Sent with preview: true, so an automation can tell a rehearsal from a real morning",
        )
        Text(s.route, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, enabled = false, label = { Text("stage: ${s.stageSlug}") })
            AssistChip(onClick = {}, enabled = false, label = { Text("stage_index: ${s.stageIndex}") })
        }
        if (s.publishing) {
            HorizontalDivider()
            Text(
                when {
                    s.snapshotProblem != null ->
                        "Could not photograph the lights: ${s.snapshotProblem}. They will be left however this preview leaves them."
                    s.snapshotCount != null ->
                        "${s.snapshotCount} lights photographed. Stopping puts every one of them back exactly as it was."
                    else -> "Photographing the lights…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (s.snapshotProblem != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Nothing else fires on the way out. The alarm never reads as ringing during a preview, so your stand-down automation is not triggered by leaving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("Rehearsing the stand-down", style = MaterialTheme.typography.titleSmall)
            Text(
                "The one thing here that is not a rehearsal. This publishes the real ringing on-to-off edge, so whatever you have hung off a dismissal runs for real, and the lights are left where it puts them rather than being put back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss for real")
            }
        }
        if (s.haScript != null) {
            Text(
                "This stage would run ${s.haScript}. The preview does not run it — publishing a state is a claim about the world, running a script is doing something to it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Two bars. Cheaper than pulling in the extended icon set for one glyph. */
@Composable
private fun PauseGlyph() {
    val colour = MaterialTheme.colorScheme.onPrimary
    Canvas(Modifier.size(18.dp)) {
        val w = size.width * 0.28f
        drawRoundRect(colour, Offset(0f, 0f), Size(w, size.height), CornerRadius(3f, 3f))
        drawRoundRect(
            colour,
            Offset(size.width - w, 0f),
            Size(w, size.height),
            CornerRadius(3f, 3f),
        )
    }
}

/**
 * The preview drives the screen the way a real ring does, and hands brightness
 * back to the system on the way out.
 */
@Composable
private fun ApplyScreenBrightness(level: Float) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(level) {
        val attrs = activity.window.attributes
        attrs.screenBrightness = if (level <= 0.01f) -1f else level.coerceIn(0.05f, 1f)
        activity.window.attributes = attrs
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val attrs = activity.window.attributes
            attrs.screenBrightness = -1f
            activity.window.attributes = attrs
        }
    }
}

private fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
