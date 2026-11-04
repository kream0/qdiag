package app.qdiag.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** One reading of the fault codes, kept to see what comes back over time. */
@Serializable
data class CodeRead(
    val time: Long,
    val stored: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val permanent: List<String> = emptyList(),
    val cleared: Boolean = false,
)

/** What a drive's health checks found (finding id → level), to follow trends. */
@Serializable
data class DriveSummary(
    val time: Long,
    val minutes: Double,
    val samples: Int,
    val findings: Map<String, String> = emptyMap(),
    val notes: Map<String, String> = emptyMap(),
)

@Serializable
data class History(
    val reads: List<CodeRead> = emptyList(),
    val drives: List<DriveSummary> = emptyList(),
)

/** The app's small history, as JSON in its files directory. */
class Store(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val _history = MutableStateFlow(read())
    val history: StateFlow<History> = _history.asStateFlow()

    private fun read(): History = try {
        if (file.exists()) AppJson.decodeFromString(History.serializer(), file.readText()) else History()
    } catch (e: Exception) {
        Log.w("Store", "history unreadable, starting fresh", e)
        History()
    }

    @Synchronized
    private fun write(h: History) {
        _history.value = h
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(History.serializer(), h))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w("Store", "could not save history", it) }
    }

    fun addRead(read: CodeRead) = write(_history.value.let { it.copy(reads = (listOf(read) + it.reads).take(200)) })

    fun addDrive(drive: DriveSummary) = write(_history.value.let { it.copy(drives = (listOf(drive) + it.drives).take(200)) })
}
