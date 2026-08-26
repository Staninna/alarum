package dev.stan.alarum.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.alarum.ui.AlarumViewModel
import dev.stan.alarum.ui.components.SectionCard
import dev.stan.alarum.ui.components.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AlarumViewModel, onDone: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entities by vm.entities.collectAsStateWithLifecycle()
    val message by vm.connectionMessage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ha = settings.ha

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = {
            message?.let {
                Snackbar(action = { TextButton(onClick = vm::clearMessage) { Text("OK") } }) {
                    Text(it)
                }
            }
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
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionCard("Home Assistant") {
                Text(
                    "Used to list your entities and to run the optional per-stage script. The house's actual behaviour comes from automations reacting to what this app publishes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ha.baseUrl,
                    onValueChange = { v -> vm.updateHa { it.copy(baseUrl = v.trim()) } },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://ha.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ha.token,
                    onValueChange = { v -> vm.updateHa { it.copy(token = v.trim()) } },
                    label = { Text("Long-lived access token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::testRest) { Text("Test") }
                    OutlinedButton(onClick = vm::loadEntities) { Text("Load entities") }
                }
            }

            SectionCard("MQTT") {
                Text(
                    "Currently publishing via ${vm.route()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "With a broker, entities are created by retained discovery and survive a restart of Home Assistant. Without one the app falls back to the REST API, which works straight away but loses the entities whenever HA restarts. Either way the connection is only open while an alarm is ringing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ha.mqttHost,
                    onValueChange = { v -> vm.updateHa { it.copy(mqttHost = v.trim()) } },
                    label = { Text("Broker host") },
                    placeholder = { Text("192.168.1.10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ha.mqttPort.toString(),
                    onValueChange = { v ->
                        vm.updateHa { it.copy(mqttPort = v.toIntOrNull() ?: it.mqttPort) }
                    },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ha.mqttUser,
                    onValueChange = { v -> vm.updateHa { it.copy(mqttUser = v.trim()) } },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ha.mqttPassword,
                    onValueChange = { v -> vm.updateHa { it.copy(mqttPassword = v) } },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    label = "TLS",
                    checked = ha.mqttTls,
                    onChange = { v -> vm.updateHa { it.copy(mqttTls = v) } },
                )
                OutlinedTextField(
                    value = ha.deviceName,
                    onValueChange = { v -> vm.updateHa { it.copy(deviceName = v) } },
                    label = { Text("Device name in HA") },
                    supportingText = {
                        Text("Home Assistant builds the entity ids from this. \"Alarum\" gives sensor.alarum_stage.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.nodeId,
                    onValueChange = { v ->
                        vm.setNodeId(v.lowercase().replace(Regex("[^a-z0-9_]"), ""))
                    },
                    label = { Text("Node id") },
                    supportingText = {
                        Text("Only change this if a second phone publishes into the same Home Assistant.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::testMqtt) { Text("Test broker") }
                    OutlinedButton(onClick = vm::publishNow) { Text("Publish now") }
                }
            }

            SectionCard("What Home Assistant sees") {
                Text(
                    "Entities are created by retained MQTT discovery, so they survive a restart of Home Assistant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    "sensor.alarum_next_alarm" to "when the next one fires",
                    "binary_sensor.alarum_ringing" to "on while it is going off",
                    "sensor.alarum_stage" to "gentle / rising / hostile, plus every field as an attribute",
                    "sensor.alarum_stage_index" to "0-based, for numeric_state triggers",
                    "sensor.alarum_elapsed" to "seconds spent ringing",
                    "sensor.alarum_last_dismissed" to "when you last shut it up",
                ).forEach { (id, what) ->
                    Column {
                        Text(id, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        Text(
                            what,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Install id: ${settings.nodeId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entities.isNotEmpty()) {
                SectionCard("Entities (${entities.size})") {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(entities, key = { it.entityId }) { e ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(e.friendlyName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${e.entityId} · ${e.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            SectionCard("NFC dismissal") {
                Text(
                    if (settings.nfcTagId.isNullOrBlank()) {
                        "No tag enrolled. Stick a tag somewhere that forces you out of bed, scan it here, and it becomes available as a dismissal method."
                    } else {
                        "Tag enrolled: ${settings.nfcTagId}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(AndroidSettings.ACTION_NFC_SETTINGS))
                    }) { Text("NFC settings") }
                    if (!settings.nfcTagId.isNullOrBlank()) {
                        TextButton(onClick = { vm.enrollNfcTag(null) }) { Text("Forget tag") }
                    }
                }
            }

            UpdatesSection()

            SectionCard("Reliability") {
                Text(
                    "Some manufacturers kill background apps regardless of what Android promises about exact alarms. Exempting Alarum from battery optimisation is the difference between waking up and explaining why you did not.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val pm = context.getSystemService(PowerManager::class.java)
                val exempt = pm.isIgnoringBatteryOptimizations(context.packageName)
                Text(
                    if (exempt) "Exempt — good." else "Not exempt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (exempt) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                if (!exempt) {
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }) { Text("Fix it") }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
