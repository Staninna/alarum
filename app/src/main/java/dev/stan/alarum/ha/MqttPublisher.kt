package dev.stan.alarum.ha

import android.util.Log
import dev.stan.alarum.data.HaSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Publishes [AlarumState] to Home Assistant over MQTT.
 *
 * Two modes, because a ring and a schedule change want opposite things:
 *
 *  - **One-shot** (`publish`) connects, publishes, disconnects. Used when an
 *    alarm is created or edited. Costs nothing at idle, which is the whole
 *    reason this app has no persistent connection.
 *  - **Session** (`openSession` .. `closeSession`) holds the connection for the
 *    duration of a ring. The phone is awake with the screen on anyway, so there
 *    is no battery argument, and stage transitions land instantly instead of
 *    paying for a fresh handshake every time.
 *
 * Every method swallows its failures. Home Assistant is an amplifier bolted on
 * to a self-sufficient local alarm; if the broker is unreachable the phone still
 * rings, still ramps, and still refuses to be dismissed.
 */
class MqttPublisher(private val settings: () -> HaSettings, private val nodeId: () -> String) {

    private val json = Json { encodeDefaults = true; explicitNulls = true }
    private val mutex = Mutex()

    @Volatile private var session: MqttClient? = null
    @Volatile private var discoverySent = false

    private val base: String get() = "alarum/${nodeId()}"
    private val stateTopic: String get() = "$base/state"

    private fun uri(s: HaSettings): String {
        val scheme = if (s.mqttTls) "ssl" else "tcp"
        return "$scheme://${s.mqttHost.trim()}:${s.mqttPort}"
    }

    private fun options(s: HaSettings) = MqttConnectOptions().apply {
        isCleanSession = true
        keepAliveInterval = 30
        connectionTimeout = 8
        isAutomaticReconnect = false
        if (s.mqttUser.isNotBlank()) {
            userName = s.mqttUser
            password = s.mqttPassword.toCharArray()
        }
    }

    private fun connect(s: HaSettings): MqttClient {
        val client = MqttClient(uri(s), "alarum-${nodeId()}", MemoryPersistence())
        client.connect(options(s))
        return client
    }

    /** Connect, publish, disconnect. For low-frequency updates. */
    suspend fun publish(state: AlarumState): Boolean = withContext(Dispatchers.IO) {
        val s = settings()
        if (!s.mqttConfigured) return@withContext false
        session?.let { return@withContext runCatching { publishOn(it, s, state) }.getOrDefault(false) }
        mutex.withLock {
            runCatching {
                val client = connect(s)
                try {
                    publishOn(client, s, state)
                } finally {
                    runCatching { client.disconnect() }
                    runCatching { client.close() }
                }
            }.getOrElse {
                Log.w(TAG, "one-shot publish failed: ${it.message}")
                false
            }
        }
    }

    /** Hold the connection open for the length of a ring. */
    suspend fun openSession(): Boolean = withContext(Dispatchers.IO) {
        val s = settings()
        if (!s.mqttConfigured) return@withContext false
        mutex.withLock {
            if (session != null) return@withLock true
            runCatching {
                session = connect(s)
                true
            }.getOrElse {
                Log.w(TAG, "session connect failed: ${it.message}")
                false
            }
        }
    }

    suspend fun closeSession() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val c = session ?: return@withLock
                session = null
                runCatching { c.disconnect() }
                runCatching { c.close() }
            }
        }
    }

    /** Publish on an already-open session, falling back to a one-shot. */
    suspend fun publishLive(state: AlarumState): Boolean = withContext(Dispatchers.IO) {
        val c = session ?: return@withContext publish(state)
        val s = settings()
        runCatching { publishOn(c, s, state) }.getOrElse {
            Log.w(TAG, "live publish failed: ${it.message}")
            false
        }
    }

    private fun publishOn(client: MqttClient, s: HaSettings, state: AlarumState): Boolean {
        if (!client.isConnected) return false
        if (!discoverySent) {
            HaDiscovery.configs(s, nodeId()).forEach { (topic, payload) ->
                client.publish(topic, retained(payload.toByteArray()))
            }
            discoverySent = true
        }
        client.publish(
            stateTopic,
            retained(json.encodeToString(AlarumState.serializer(), state).toByteArray()),
        )
        return true
    }

    private fun retained(payload: ByteArray) = MqttMessage(payload).apply {
        qos = 1
        isRetained = true
    }

    /** Force discovery to be re-sent, e.g. after the settings change. */
    fun invalidateDiscovery() {
        discoverySent = false
    }

    suspend fun test(): String = withContext(Dispatchers.IO) {
        val s = settings()
        if (!s.mqttConfigured) return@withContext "No broker host set"
        runCatching {
            val client = connect(s)
            client.publish("$base/test", MqttMessage("hello".toByteArray()).apply { qos = 1 })
            client.disconnect()
            client.close()
            "Connected to ${s.mqttHost}:${s.mqttPort}"
        }.getOrElse { "Failed: ${it.message ?: it::class.simpleName}" }
    }

    private companion object {
        const val TAG = "AlarumMqtt"
    }
}
