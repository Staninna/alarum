package dev.stan.alarum.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.alarum.domain.DismissalMethod
import dev.stan.alarum.domain.EscalationProfile
import dev.stan.alarum.domain.Defaults
import dev.stan.alarum.domain.Sounds
import dev.stan.alarum.domain.Stage
import dev.stan.alarum.domain.VibePattern
import dev.stan.alarum.ui.AlarumViewModel
import dev.stan.alarum.ui.components.LabeledSlider
import dev.stan.alarum.ui.components.Picker
import dev.stan.alarum.ui.components.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    vm: AlarumViewModel,
    profileId: String,
    onDone: () -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val source = profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull()
    if (source == null) {
        onDone()
        return
    }

    var profile by remember(profileId) { mutableStateOf(source) }
    var expanded by remember { mutableStateOf<String?>(profile.stages.firstOrNull()?.id) }

    fun mutate(index: Int, block: (Stage) -> Stage) {
        profile = profile.copy(
            stages = profile.stages.mapIndexed { i, s -> if (i == index) block(s) else s },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escalation") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.duplicateProfile(profile) }) { Text("Duplicate") }
                    TextButton(onClick = { vm.saveProfile(profile); onDone() }) { Text("Save") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val added = Defaults.newStage(profile.stages.size)
                    // New stages land before the final one, because the final
                    // stage is the unbounded "still ringing" state and should
                    // stay last.
                    val at = (profile.stages.size - 1).coerceAtLeast(0)
                    profile = profile.copy(
                        stages = profile.stages.toMutableList().apply { add(at, added) },
                    )
                    expanded = added.id
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add stage") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { profile = profile.copy(name = it) },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { Timeline(profile) }

            itemsIndexed(profile.stages, key = { _, s -> s.id }) { index, stage ->
                StageCard(
                    stage = stage,
                    index = index,
                    total = profile.stages.size,
                    expanded = expanded == stage.id,
                    nfcEnrolled = !settings.nfcTagId.isNullOrBlank(),
                    onToggle = { expanded = if (expanded == stage.id) null else stage.id },
                    onChange = { mutate(index) { _ -> it } },
                    onMove = { delta ->
                        val target = index + delta
                        if (target in profile.stages.indices) {
                            val list = profile.stages.toMutableList()
                            list.add(target, list.removeAt(index))
                            profile = profile.copy(stages = list)
                        }
                    },
                    onDelete = {
                        if (profile.stages.size > 1) {
                            profile = profile.copy(
                                stages = profile.stages.filterNot { it.id == stage.id },
                            )
                        }
                    },
                )
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

/**
 * The shape of the wake-up, at a glance. Width is proportional to duration,
 * height and colour to how loud it gets, so you can see whether a profile ramps
 * or simply detonates.
 */
@Composable
private fun Timeline(profile: EscalationProfile) {
    val scheme = MaterialTheme.colorScheme
    val calm = scheme.primary.copy(alpha = 0.45f)
    val angry = scheme.error

    Column {
        Text(
            "The shape of it",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    // The final stage has no duration; give it a visual share so
                    // it does not vanish from the picture entirely.
                    val weights = profile.stages.mapIndexed { i, s ->
                        if (i == profile.stages.lastIndex) {
                            (profile.rampSec * 0.25f).coerceAtLeast(120f)
                        } else {
                            s.durationSec.toFloat().coerceAtLeast(1f)
                        }
                    }
                    val total = weights.sum().coerceAtLeast(1f)
                    var x = 0f
                    val gap = 4f
                    profile.stages.forEachIndexed { i, s ->
                        val w = (weights[i] / total) * (size.width - gap * (profile.stages.size - 1))
                        val peak = maxOf(s.audio.endLevel, s.audio.startLevel)
                        val h = size.height * (0.18f + 0.82f * peak)
                        drawRoundRect(
                            color = lerp(calm, angry, peak),
                            topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                        )
                        x += w + gap
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    buildString {
                        append(profile.stages.size)
                        append(" stages · ")
                        append(dev.stan.alarum.ui.list.formatMinutes(profile.rampSec))
                        append(" of ramp, then the last one holds until you deal with it")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StageCard(
    stage: Stage,
    index: Int,
    total: Int,
    expanded: Boolean,
    nfcEnrolled: Boolean,
    onToggle: () -> Unit,
    onChange: (Stage) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val isFinal = index == total - 1
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            lerp(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.error,
                                maxOf(stage.audio.endLevel, stage.audio.startLevel),
                            ),
                            RoundedCornerShape(5.dp),
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stage.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isFinal) "Holds until dismissed · ${stage.dismissal.method.label}"
                        else "${dev.stan.alarum.ui.list.formatMinutes(stage.durationSec)} · ${stage.dismissal.method.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HorizontalDivider()

                    OutlinedTextField(
                        value = stage.name,
                        onValueChange = { onChange(stage.copy(name = it)) },
                        label = { Text("Stage name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isFinal) {
                        Text(
                            "This is the last stage, so it has no duration — it sustains until the alarm is dealt with.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LabeledSlider(
                            label = "Duration",
                            value = stage.durationSec / 60f,
                            onChange = {
                                onChange(stage.copy(durationSec = (it * 60).toInt()))
                            },
                            valueRange = 0.25f..30f,
                            display = { "%.1f min".format(it) },
                        )
                    }

                    Text("Sound", style = MaterialTheme.typography.titleSmall)
                    Picker(
                        label = "Tone",
                        options = Sounds.builtins,
                        selected = stage.audio.sound,
                        optionLabel = { Sounds.label(it) },
                        onSelect = { onChange(stage.copy(audio = stage.audio.copy(sound = it))) },
                    )
                    LabeledSlider(
                        label = "Volume at the start",
                        value = stage.audio.startLevel,
                        onChange = { onChange(stage.copy(audio = stage.audio.copy(startLevel = it))) },
                    )
                    LabeledSlider(
                        label = "Volume by the end",
                        value = stage.audio.endLevel,
                        onChange = { onChange(stage.copy(audio = stage.audio.copy(endLevel = it))) },
                    )
                    SwitchRow(
                        label = "Take over system volume",
                        checked = stage.audio.commandeerSystemVolume,
                        onChange = {
                            onChange(stage.copy(audio = stage.audio.copy(commandeerSystemVolume = it)))
                        },
                        help = "Pins the alarm stream to maximum, and puts it back afterwards",
                    )

                    HorizontalDivider()
                    Text("Phone", style = MaterialTheme.typography.titleSmall)
                    Picker(
                        label = "Vibration",
                        options = VibePattern.entries,
                        selected = stage.haptics.pattern,
                        optionLabel = { it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) },
                        onSelect = { onChange(stage.copy(haptics = stage.haptics.copy(pattern = it))) },
                    )
                    if (stage.haptics.pattern != VibePattern.NONE) {
                        LabeledSlider(
                            label = "Vibration strength",
                            value = stage.haptics.amplitude / 255f,
                            onChange = {
                                onChange(
                                    stage.copy(
                                        haptics = stage.haptics.copy(
                                            amplitude = (it * 255).toInt().coerceIn(1, 255),
                                        ),
                                    ),
                                )
                            },
                        )
                    }
                    LabeledSlider(
                        label = "Screen brightness",
                        value = stage.flash.screenBrightness,
                        onChange = { onChange(stage.copy(flash = stage.flash.copy(screenBrightness = it))) },
                        display = { if (it <= 0.01f) "Off" else "%.0f%%".format(it * 100) },
                    )
                    LabeledSlider(
                        label = "Torch strobe",
                        value = stage.flash.torchHz,
                        onChange = { onChange(stage.copy(flash = stage.flash.copy(torchHz = it))) },
                        valueRange = 0f..10f,
                        display = { if (it < 0.2f) "Off" else "%.1f Hz".format(it) },
                    )

                    HorizontalDivider()
                    Text("Getting rid of it", style = MaterialTheme.typography.titleSmall)
                    Picker(
                        label = "Dismissal",
                        options = DismissalMethod.entries,
                        selected = stage.dismissal.method,
                        optionLabel = {
                            if (it == DismissalMethod.NFC && !nfcEnrolled) "${it.label} (no tag enrolled)"
                            else it.label
                        },
                        onSelect = {
                            if (it != DismissalMethod.NFC || nfcEnrolled) {
                                onChange(stage.copy(dismissal = stage.dismissal.copy(method = it)))
                            }
                        },
                    )
                    if (stage.dismissal.method == DismissalMethod.NFC && !nfcEnrolled) {
                        Text(
                            "Enrol a tag in Settings before using this. Until then the stage would be impossible to dismiss.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (stage.dismissal.method != DismissalMethod.TAP) {
                        LabeledSlider(
                            label = "Difficulty",
                            value = stage.dismissal.difficulty.toFloat(),
                            onChange = {
                                onChange(
                                    stage.copy(
                                        dismissal = stage.dismissal.copy(
                                            difficulty = it.toInt().coerceIn(1, 5),
                                        ),
                                    ),
                                )
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                            display = { "${it.toInt()} / 5" },
                        )
                    }
                    SwitchRow(
                        label = "Allow snooze",
                        checked = stage.allowSnooze,
                        onChange = { onChange(stage.copy(allowSnooze = it)) },
                    )

                    HorizontalDivider()
                    Text("Home Assistant", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The house reacts to the stage this app publishes, so most people want an automation rather than anything here. This is the shortcut: a script or scene to run the moment this stage begins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = stage.haScript.orEmpty(),
                        onValueChange = { onChange(stage.copy(haScript = it.ifBlank { null })) },
                        label = { Text("script.x or scene.y (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("stage_slug: ${dev.stan.alarum.ha.AlarumState.slug(stage.name)}") },
                    )

                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move earlier")
                        }
                        IconButton(onClick = { onMove(1) }, enabled = index < total - 1) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move later")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDelete, enabled = total > 1) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete stage",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
