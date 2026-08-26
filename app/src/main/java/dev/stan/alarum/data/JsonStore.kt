package dev.stan.alarum.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * A small write-through JSON file.
 *
 * Writes go to a sibling temp file and are renamed into place, so a process
 * death mid-write leaves the previous good copy rather than a truncated one.
 * That matters more than usual here: a corrupt alarm file means no alarm.
 */
class JsonStore<T>(
    context: Context,
    fileName: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val file = File(context.filesDir, fileName)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun read(): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                if (!file.exists()) return@runCatching default()
                json.decodeFromString(serializer, file.readText())
            }.getOrElse { default() }
        }
    }

    suspend fun write(value: T) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            tmp.renameTo(file)
        }
        Unit
    }
}
