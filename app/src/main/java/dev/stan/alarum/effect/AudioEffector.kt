package dev.stan.alarum.effect

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import dev.stan.alarum.domain.Sounds
import kotlin.concurrent.thread
import kotlin.math.pow

/**
 * Renders the current tone at the current level, and keeps doing it until told
 * to stop.
 *
 * Plays on `USAGE_ALARM`, which is what gets the audio through Do Not Disturb
 * and past a silenced ringer. Level is applied with a perceptual curve, because
 * a linear ramp spends its first half inaudible and its second half shouting.
 */
class AudioEffector(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Volatile private var level: Float = 0f
    @Volatile private var soundId: String = Sounds.SOFT_CHIME
    @Volatile private var running = false
    private var worker: Thread? = null
    private var track: AudioTrack? = null
    private var priorAlarmVolume: Int? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "alarum-audio", isDaemon = true) { render() }
    }

    fun update(soundId: String, level: Float) {
        if (this.soundId != soundId) {
            this.soundId = soundId
            phaseReset = true
        }
        this.level = level.coerceIn(0f, 1f)
    }

    /**
     * Take the system alarm stream to maximum so the app's own gain is the only
     * thing deciding loudness. The previous value is restored on stop, because
     * silently leaving someone's alarm volume pinned is rude.
     */
    fun commandeerSystemVolume(take: Boolean) {
        runCatching {
            if (take) {
                if (priorAlarmVolume == null) {
                    priorAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                }
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        }.onFailure { Log.w(TAG, "volume takeover failed: ${it.message}") }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
        runCatching { track?.pause(); track?.flush(); track?.release() }
        track = null
        priorAlarmVolume?.let { prior ->
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, prior, 0) }
            priorAlarmVolume = null
        }
    }

    @Volatile private var phaseReset = false

    private fun render() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 4)

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()

        val chunk = ShortArray(CHUNK_SAMPLES)
        var sampleIndex = 0L

        while (running) {
            if (phaseReset) {
                sampleIndex = 0L
                phaseReset = false
            }
            val voice = ToneSynth.voice(soundId)
            // Perceptual curve: quiet stays genuinely quiet for longer.
            val gain = level.toDouble().pow(2.5)
            for (i in chunk.indices) {
                val tSec = (sampleIndex + i).toDouble() / SAMPLE_RATE
                val s = voice(tSec) * gain
                chunk[i] = (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            sampleIndex += chunk.size
            val written = runCatching { t.write(chunk, 0, chunk.size) }.getOrDefault(-1)
            if (written < 0) break
        }
    }

    private companion object {
        const val TAG = "AlarumAudio"
        const val SAMPLE_RATE = 44100
        const val CHUNK_SAMPLES = 4410 // 100ms, so level changes land promptly
    }
}
