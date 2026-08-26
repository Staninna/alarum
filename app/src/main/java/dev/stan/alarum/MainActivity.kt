package dev.stan.alarum

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.stan.alarum.domain.Alarm
import dev.stan.alarum.domain.EscalationProfile
import dev.stan.alarum.ui.AlarumViewModel
import dev.stan.alarum.ui.editor.AlarmEditorScreen
import dev.stan.alarum.ui.editor.ProfileEditorScreen
import dev.stan.alarum.ui.list.AlarmListScreen
import dev.stan.alarum.ui.preview.PreviewScreen
import dev.stan.alarum.ui.settings.SettingsScreen
import dev.stan.alarum.ui.theme.AlarumTheme

private sealed interface Screen {
    data object List : Screen
    data class EditAlarm(val alarm: Alarm) : Screen
    /**
     * [draft] carries unsaved edits back from the previewer, so going away to
     * listen to a change does not throw the change away.
     */
    data class EditProfile(val profileId: String, val draft: EscalationProfile? = null) : Screen
    /**
     * Carries the profile itself rather than an id: the previewer runs what you
     * are editing, which may never have been saved. [from] is where Back goes,
     * since a preview can be started from the list or from mid-edit.
     */
    data class Preview(val profile: EscalationProfile, val from: Screen) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotifications()

        setContent {
            AlarumTheme {
                val vm: AlarumViewModel = viewModel()
                var screen by remember { mutableStateOf<Screen>(Screen.List) }

                when (val s = screen) {
                    Screen.List -> AlarmListScreen(
                        vm = vm,
                        onAdd = { screen = Screen.EditAlarm(vm.newAlarm()) },
                        onEdit = { screen = Screen.EditAlarm(it) },
                        onSettings = { screen = Screen.Settings },
                        onEditProfile = { screen = Screen.EditProfile(it) },
                        onPreviewProfile = { screen = Screen.Preview(it, from = Screen.List) },
                    )

                    is Screen.EditAlarm -> AlarmEditorScreen(
                        vm = vm,
                        initial = s.alarm,
                        onDone = { screen = Screen.List },
                        onEditProfile = { screen = Screen.EditProfile(it) },
                    )

                    is Screen.EditProfile -> ProfileEditorScreen(
                        vm = vm,
                        profileId = s.profileId,
                        draft = s.draft,
                        onDone = { screen = Screen.List },
                        onPreview = { screen = Screen.Preview(it, from = s.copy(draft = it)) },
                    )

                    is Screen.Preview -> PreviewScreen(
                        vm = vm,
                        profile = s.profile,
                        onDone = { screen = s.from },
                    )

                    Screen.Settings -> SettingsScreen(
                        vm = vm,
                        onDone = { screen = Screen.List },
                    )
                }
            }
        }
    }

    private fun askForNotifications() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
