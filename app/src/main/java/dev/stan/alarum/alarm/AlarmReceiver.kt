package dev.stan.alarum.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Wakes on the exact alarm and hands straight over to the foreground service.
 *
 * Deliberately does almost nothing: a receiver gets about ten seconds before
 * Android kills it, which is not enough to run a fifteen-minute escalation.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        Log.i(TAG, "alarm fired: $alarmId")

        val service = Intent(context, RingService::class.java).apply {
            action = RingService.ACTION_START
            putExtra(EXTRA_ALARM_ID, alarmId)
            // When the ramp was supposed to begin. Differs from now for an
            // awake-by alarm scheduled with less than a ramp to spare, and
            // whenever the OS fires us late.
            putExtra(EXTRA_STARTED_AT, intent.getLongExtra(EXTRA_STARTED_AT, 0L))
        }
        context.startForegroundService(service)
    }

    companion object {
        const val ACTION_FIRE = "dev.stan.alarum.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_STARTED_AT = "started_at"
        private const val TAG = "AlarumReceiver"
    }
}
