package dev.stan.alarum.ui.list

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.alarum.domain.Alarm
import dev.stan.alarum.domain.EscalationProfile
import dev.stan.alarum.domain.Schedule
import dev.stan.alarum.ui.AlarumViewModel
import java.time.Duration
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    vm: AlarumViewModel,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onSettings: () -> Unit,
    onEditProfile: (String) -> Unit,
    onPreviewProfile: (EscalationProfile) -> Unit,
) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    val next = alarms
        .mapNotNull { a -> Schedule.nextOccurrence(a, ZonedDateTime.now())?.let { a to it } }
        .minByOrNull { it.second }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarum") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add alarm")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NextUpHeader(next) }

            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    profileName = profiles.firstOrNull { it.id == alarm.profileId }?.name ?: "—",
                    onClick = { onEdit(alarm) },
                    onToggle = { vm.toggle(alarm, it) },
                )
            }

            if (alarms.isEmpty()) {
                item {
                    Text(
                        "No alarms yet. The one you add first is the one you will resent most.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    "Escalation profiles",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(profiles, key = { it.id }) { p ->
                Surface(
                    onClick = { onEditProfile(p.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.List, contentDescription = null)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${p.stages.size} stages · ${formatMinutes(p.rampSec)} before the last one",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StagePreviewBar(p.stages.size)
                        IconButton(onClick = { onPreviewProfile(p) }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Preview ${p.name}",
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun NextUpHeader(next: Pair<Alarm, ZonedDateTime>?) {
    Column(Modifier.padding(vertical = 24.dp)) {
        if (next == null) {
            Text(
                "Nothing scheduled",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val (alarm, at) = next
            val until = Duration.between(ZonedDateTime.now(), at)
            Text(
                "%02d:%02d".format(alarm.hour, alarm.minute),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                buildString {
                    append(alarm.label.ifBlank { "Next alarm" })
                    append(" · in ")
                    append(humanise(until))
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    profileName: String,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (alarm.enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        Schedule.daysLabel(alarm.days),
                        alarm.label.takeIf { it.isNotBlank() },
                        profileName,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

/** A tiny visual hint of how many steps the ramp has. */
@Composable
private fun StagePreviewBar(stages: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(stages.coerceAtMost(6)) { i ->
            val frac = (i + 1f) / stages.coerceAtLeast(1)
            Box(
                Modifier
                    .width(5.dp)
                    .height((8 + 20 * frac).dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * frac),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

private fun humanise(d: Duration): String {
    val total = d.toMinutes().coerceAtLeast(0)
    val days = total / 1440
    val hours = (total % 1440) / 60
    val minutes = total % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

internal fun formatMinutes(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (s == 0) "${m}m" else "${m}m ${s}s"
}
