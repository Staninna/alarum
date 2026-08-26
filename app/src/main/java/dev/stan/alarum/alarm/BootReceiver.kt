package dev.stan.alarum.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.stan.alarum.AlarumApp
import kotlinx.coroutines.launch

/**
 * Exact alarms do not survive a reboot, an app update, or a clock change, so
 * every one of those has to put them back.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as AlarumApp
        app.scope.launch {
            try {
                app.repository.loadIfNeeded()
                app.scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
