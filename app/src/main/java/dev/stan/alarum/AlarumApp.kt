package dev.stan.alarum

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.stan.alarum.alarm.AlarmScheduler
import dev.stan.alarum.data.AlarumRepository
import dev.stan.alarum.ha.HaRest
import dev.stan.alarum.ha.MqttPublisher
import dev.stan.alarum.ha.StatePublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarumApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: AlarumRepository private set
    lateinit var scheduler: AlarmScheduler private set
    lateinit var haRest: HaRest private set
    lateinit var mqtt: MqttPublisher private set
    lateinit var publisher: StatePublisher private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        repository = AlarumRepository(this, scope)
        haRest = HaRest { repository.settings.value.ha }
        mqtt = MqttPublisher(
            settings = { repository.settings.value.ha },
            nodeId = { repository.settings.value.nodeId },
        )
        publisher = StatePublisher(
            settings = { repository.settings.value.ha },
            mqtt = mqtt,
            rest = haRest,
        )
        scheduler = AlarmScheduler(this, repository, publisher, scope)

        createChannels()

        scope.launch {
            repository.load()
            if (repository.settings.value.nodeId.isBlank()) {
                repository.updateSettings { it.copy(nodeId = "alarum") }
            }
            scheduler.rescheduleAll()
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RINGING,
                "Ringing alarm",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Shown while an alarm is going off"
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPCOMING,
                "Upcoming alarm",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Quiet note about the next alarm" },
        )
    }

    companion object {
        const val CHANNEL_RINGING = "alarum.ringing"
        const val CHANNEL_UPCOMING = "alarum.upcoming"

        lateinit var instance: AlarumApp private set
    }
}
