package dev.stan.alarum.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.alarum.domain.Alarm
import dev.stan.alarum.domain.Schedule
import dev.stan.alarum.ui.AlarumViewModel
import dev.stan.alarum.ui.components.Picker
import dev.stan.alarum.ui.components.SectionCard
import dev.stan.alarum.ui.components.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    vm: AlarumViewModel,
    initial: Alarm,
    onDone: () -> Unit,
    onEditProfile: (String) -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var alarm by remember { mutableStateOf(initial) }
    val existing = vm.alarms.collectAsStateWithLifecycle().value.any { it.id == initial.id }

    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing) "Edit alarm" else "New alarm") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (existing) {
                        IconButton(onClick = { vm.delete(alarm.id); onDone() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(onClick = {
                        vm.save(
                            alarm.copy(hour = timeState.hour, minute = timeState.minute),
                        )
                        onDone()
                    }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TimePicker(state = timeState)
                }
            }

            SectionCard("Repeat") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = day in alarm.days,
                            onClick = {
                                alarm = alarm.copy(
                                    days = if (day in alarm.days) alarm.days - day
                                    else alarm.days + day,
                                )
                            },
                            label = { Text(Schedule.dayLabel(day)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { alarm = alarm.copy(days = Schedule.weekdays) }) {
                        Text("Weekdays")
                    }
                    TextButton(onClick = { alarm = alarm.copy(days = Schedule.everyDay) }) {
                        Text("Every day")
                    }
                    TextButton(onClick = { alarm = alarm.copy(days = emptySet()) }) {
                        Text("Once")
                    }
                }
                Text(
                    if (alarm.isRepeating) Schedule.daysLabel(alarm.days)
                    else "Fires once, then switches itself off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("What that time means") {
                SwitchRow(
                    label = "Be awake by then",
                    checked = alarm.awakeBy,
                    onChange = { alarm = alarm.copy(awakeBy = it) },
                    help = if (alarm.awakeBy) {
                        "The ramp finishes at %02d:%02d, so it starts earlier".format(
                            timeState.hour, timeState.minute,
                        )
                    } else {
                        "It starts ringing at %02d:%02d and escalates from there".format(
                            timeState.hour, timeState.minute,
                        )
                    },
                )
                val ramp = profiles.firstOrNull { it.id == alarm.profileId }?.rampSec ?: 0
                Text(
                    if (alarm.awakeBy) {
                        val start = java.time.LocalTime.of(timeState.hour, timeState.minute)
                            .minusSeconds(ramp.toLong())
                        "Gently from %02d:%02d, at its worst by %02d:%02d.".format(
                            start.hour, start.minute, timeState.hour, timeState.minute,
                        )
                    } else {
                        val worst = java.time.LocalTime.of(timeState.hour, timeState.minute)
                            .plusSeconds(ramp.toLong())
                        "Gently from %02d:%02d, at its worst by %02d:%02d.".format(
                            timeState.hour, timeState.minute, worst.hour, worst.minute,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Details") {
                OutlinedTextField(
                    value = alarm.label,
                    onValueChange = { alarm = alarm.copy(label = it) },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker(
                    label = "Escalation profile",
                    options = profiles,
                    selected = profiles.firstOrNull { it.id == alarm.profileId }
                        ?: profiles.first(),
                    optionLabel = { "${it.name} · ${it.stages.size} stages" },
                    onSelect = { alarm = alarm.copy(profileId = it.id) },
                )
                OutlinedButton(
                    onClick = { onEditProfile(alarm.profileId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Edit this profile") }

                Picker(
                    label = "Snooze length",
                    options = listOf(1, 3, 5, 9, 10, 15, 20),
                    selected = alarm.snoozeMinutes,
                    optionLabel = { "$it minutes" },
                    onSelect = { alarm = alarm.copy(snoozeMinutes = it) },
                )
            }

            SectionCard("Try it") {
                Text(
                    "Rings straight away so you can hear the ramp without setting an alarm for two minutes from now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            vm.testRing(alarm.copy(hour = timeState.hour, minute = timeState.minute))
                        },
                    ) { Text("Ring now") }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
