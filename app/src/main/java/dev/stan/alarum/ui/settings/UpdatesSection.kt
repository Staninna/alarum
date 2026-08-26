package dev.stan.alarum.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.alarum.ui.components.SectionCard
import dev.stan.alarum.update.Release
import dev.stan.alarum.update.Updater
import kotlinx.coroutines.launch

/**
 * One button that asks GitHub for the newest release, and — only when there is
 * a newer one — a second that fetches that APK and installs it.
 *
 * Nothing here happens on its own. An alarm clock that replaced itself
 * unattended at 03:00 would be an inventive way to miss a morning.
 */
@Composable
fun UpdatesSection() {
    val context = LocalContext.current
    val updater = remember { Updater.get(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val busy = state is Updater.State.Checking ||
        state is Updater.State.Downloading ||
        state is Updater.State.Installing

    SectionCard("Updates") {
        Text(
            "Version ${updater.currentVersion} · Staninna/alarum",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { updater.check() } },
                enabled = !busy,
            ) {
                Text(if (state is Updater.State.Checking) "Checking…" else "Check for updates")
            }
            if (state !is Updater.State.Idle && !busy) {
                TextButton(onClick = updater::dismiss) { Text("Dismiss") }
            }
        }

        when (val s = state) {
            is Updater.State.Idle, is Updater.State.Checking -> Unit

            is Updater.State.UpToDate -> Text(
                "Up to date on ${s.version}.",
                style = MaterialTheme.typography.bodyMedium,
            )

            is Updater.State.Failed -> Text(
                s.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            is Updater.State.Available -> AvailableUpdate(
                release = s.release,
                canInstall = updater.canInstallPackages(),
                onGrant = { context.startActivity(updater.unknownSourcesIntent()) },
                onInstall = { scope.launch { updater.downloadAndInstall(s.release) } },
            )

            is Updater.State.Downloading -> {
                val total = s.totalBytes.takeIf { it > 0 }
                Text(
                    if (total != null) "Downloading ${s.downloadedBytes.mb()} of ${total.mb()}"
                    else "Downloading ${s.downloadedBytes.mb()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (total != null) {
                    LinearProgressIndicator(
                        progress = { (s.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
            }

            is Updater.State.Installing -> Text(
                "Handing it to Android…",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AvailableUpdate(
    release: Release,
    canInstall: Boolean,
    onGrant: () -> Unit,
    onInstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${release.tag} is available (${release.sizeBytes.mb()})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (release.notes.isNotBlank()) {
            Text(
                release.notes.lineSequence().take(8).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canInstall) {
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Text("Download and install")
            }
        } else {
            Text(
                "Android needs permission to let Alarum install apps before this can run.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                Text("Grant permission")
            }
        }
    }
}

private fun Long.mb(): String = "%.1f MB".format(this / 1_048_576.0)
