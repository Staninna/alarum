package dev.stan.alarum.ui.ring

import android.app.Activity
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.alarum.alarm.Ringing
import dev.stan.alarum.ui.theme.AlarumTheme
import kotlin.math.sqrt

/**
 * The screen you meet at 07:00.
 *
 * Shows over the lockscreen and turns the display on by itself. Screen
 * brightness follows the current stage, so the gentle phase does not blind you
 * and the hostile phase very much does.
 */
class RingActivity : ComponentActivity(), SensorEventListener {

    private val shakeCount = mutableIntStateOf(0)
    private val scannedTag = mutableStateOf<String?>(null)
    private var sensorManager: SensorManager? = null
    private var lastShakeAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
        sensorManager = getSystemService(SensorManager::class.java)

        // The back gesture is not a dismissal method. Overriding onBackPressed
        // no longer covers gesture navigation, so the dispatcher is the only
        // thing that actually holds the screen shut.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        setContent {
            AlarumTheme(dark = true) {
                val state by Ringing.state.collectAsStateWithLifecycle()
                if (state == null) {
                    finishAndRemoveTask()
                } else {
                    applyBrightness(state!!.screenBrightness)
                    RingScreen(
                        state = state!!,
                        shakeCount = shakeCount.intValue,
                        scannedTag = scannedTag.value,
                        onDismiss = { Ringing.onDismiss?.invoke() },
                        onSnooze = { Ringing.onSnooze?.invoke() },
                        onResetShakes = { shakeCount.intValue = 0 },
                    )
                }
            }
        }
    }

    private fun applyBrightness(level: Float) {
        val attrs = window.attributes
        // -1 hands control back to the system rather than pinning it dark.
        attrs.screenBrightness = if (level <= 0.01f) -1f else level.coerceIn(0.05f, 1f)
        window.attributes = attrs
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        enableNfcReader()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        runCatching { NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) }
    }

    private fun enableNfcReader() {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        runCatching {
            adapter.enableReaderMode(
                this,
                { tag: Tag -> scannedTag.value = tag.id.joinToString("") { "%02X".format(it) } },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null,
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        // A real shake, not a roll-over: well above gravity, and rate limited so
        // one vigorous movement counts once.
        if (g > 2.2f && now - lastShakeAt > 220) {
            lastShakeAt = now
            shakeCount.intValue += 1
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
