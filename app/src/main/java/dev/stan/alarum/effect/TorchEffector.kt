package dev.stan.alarum.effect

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * Camera torch strobe.
 *
 * `setTorchMode` needs no camera permission, but it does throw if another app
 * holds the camera, so every call is guarded — a failed strobe must never take
 * down the ring.
 */
class TorchEffector(context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var on = false
    private var hz = 0f

    fun apply(hz: Float) {
        val id = cameraId ?: return
        if (this.hz == hz) return
        this.hz = hz
        if (hz <= 0f) {
            stop()
            return
        }
        if (handler == null) {
            thread = HandlerThread("alarum-torch").also { it.start() }
            handler = Handler(thread!!.looper)
        }
        handler?.removeCallbacksAndMessages(null)
        handler?.post(object : Runnable {
            override fun run() {
                if (this@TorchEffector.hz <= 0f) return
                on = !on
                runCatching { cameraManager.setTorchMode(id, on) }
                    .onFailure { Log.w(TAG, "torch: ${it.message}") }
                handler?.postDelayed(this, (500f / this@TorchEffector.hz).toLong().coerceAtLeast(30L))
            }
        })
    }

    fun stop() {
        hz = 0f
        handler?.removeCallbacksAndMessages(null)
        val id = cameraId
        if (id != null && on) {
            runCatching { cameraManager.setTorchMode(id, false) }
        }
        on = false
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private companion object {
        const val TAG = "AlarumTorch"
    }
}
