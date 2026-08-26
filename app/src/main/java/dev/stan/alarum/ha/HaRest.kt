package dev.stan.alarum.ha

import dev.stan.alarum.data.HaSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class HaEntity(
    val entityId: String,
    val friendlyName: String,
    val state: String,
) {
    val domain: String get() = entityId.substringBefore('.')
}

sealed interface HaResult<out T> {
    data class Ok<T>(val value: T) : HaResult<T>
    data class Failed(val reason: String) : HaResult<Nothing>
}

/**
 * Thin REST client. Used for two things only: listing entities so the pickers
 * show real names, and firing the optional per-stage script or scene.
 *
 * The house's actual escalation lives in Home Assistant automations driven by
 * the MQTT state this app publishes, so nothing on the critical path of a ring
 * depends on this class succeeding.
 */
class HaRest(private val settings: () -> HaSettings) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun request(path: String): Request.Builder {
        val s = settings()
        return Request.Builder()
            .url("${s.apiBase}$path")
            .header("Authorization", "Bearer ${s.token}")
            .header("Content-Type", "application/json")
    }

    suspend fun ping(): HaResult<String> = call {
        val body = execute(request("/").get().build())
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        message ?: "Connected"
    }

    suspend fun states(): HaResult<List<HaEntity>> = call {
        val body = execute(request("/states").get().build())
        json.parseToJsonElement(body).jsonArray.map { el ->
            val o = el.jsonObject
            val id = o["entity_id"]!!.jsonPrimitive.content
            HaEntity(
                entityId = id,
                friendlyName = o["attributes"]?.jsonObject?.get("friendly_name")
                    ?.jsonPrimitive?.content ?: id,
                state = o["state"]?.jsonPrimitive?.content.orEmpty(),
            )
        }.sortedBy { it.entityId }
    }

    /** Fires a service and does not care much whether it worked. */
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        extra: JsonObject? = null,
    ): HaResult<Unit> = call {
        val payload = buildString {
            append("{")
            if (entityId != null) append("\"entity_id\":\"$entityId\"")
            if (extra != null && extra.isNotEmpty()) {
                if (entityId != null) append(",")
                append(extra.entries.joinToString(",") { "\"${it.key}\":${it.value}" })
            }
            append("}")
        }
        execute(
            request("/services/$domain/$service")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build(),
        )
        Unit
    }

    /**
     * Creates or updates an entity directly.
     *
     * These entities are ephemeral — Home Assistant forgets them on restart
     * until something publishes again — which is exactly why MQTT discovery is
     * the preferred route when a broker exists.
     */
    suspend fun setState(
        entityId: String,
        state: String,
        attributes: Map<String, String>,
    ): HaResult<Unit> = call {
        val attrs = attributes.entries.joinToString(",") { (k, v) ->
            "\"" + k + "\":\"" + escape(v) + "\""
        }
        val payload = "{\"state\":\"" + escape(state) + "\",\"attributes\":{" + attrs + "}}"
        execute(
            request("/states/$entityId")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build(),
        )
        Unit
    }

    private fun escape(v: String): String =
        v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    /** Runs a `script.x` or `scene.y` entity, whichever was configured. */
    suspend fun runEntity(entityId: String): HaResult<Unit> {
        val domain = entityId.substringBefore('.')
        return when (domain) {
            "scene" -> callService("scene", "turn_on", entityId)
            "script" -> callService("script", "turn_on", entityId)
            "automation" -> callService("automation", "trigger", entityId)
            else -> callService(domain, "turn_on", entityId)
        }
    }

    private fun execute(req: Request): String = client.newCall(req).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) error("HTTP ${res.code}${if (body.isBlank()) "" else ": ${body.take(180)}"}")
        body
    }

    private suspend fun <T> call(block: () -> T): HaResult<T> = withContext(Dispatchers.IO) {
        val s = settings()
        if (!s.restConfigured) return@withContext HaResult.Failed("Home Assistant URL or token missing")
        runCatching { block() }
            .fold({ HaResult.Ok(it) }, { HaResult.Failed(it.message ?: it::class.simpleName.orEmpty()) })
    }
}
