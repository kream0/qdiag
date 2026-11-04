package app.qdiag.diag

import android.content.Context
import android.os.Build
import android.util.Log
import app.qdiag.BuildConfig
import app.qdiag.core.elm.Discovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

/**
 * Diagnostics for the developer, who has no car to test on: what the app sends to the adapter
 * and what comes back, the codes read, a summary of live values every few seconds, errors and
 * crashes, as JSON lines posted to a ntfy.sh topic and read back through
 * .github/workflows/diag.yml. The phone may be on the adapter's Wi-Fi (no internet), so lines
 * wait in a file until a post gets through. Never location; anything that looks like a VIN is
 * masked. Settings → Diagnostics.
 */
object Diag {
    const val URL = "https://ntfy.sh/qdiag-diag-e20390278516129da12a"
    private const val TAG = "Diag"
    /** ntfy.sh keeps messages up to 4 KB as text; a longer one becomes a short-lived attachment. */
    private const val MAX_BYTES = 3_500
    private const val MAX_LINES = 6_000

    /** This run of the app, to tell runs apart in the log. */
    val session: String = UUID.randomUUID().toString().take(6)
    private val lines = ArrayList<String>()
    private var file: File? = null
    @Volatile private var http: OkHttpClient? = null
    @Volatile private var enabled: () -> Boolean = { false }

    fun start(context: Context, client: OkHttpClient, scope: CoroutineScope, isEnabled: () -> Boolean) {
        http = client
        enabled = isEnabled
        file = File(context.filesDir, "diag-queue.jsonl")
        // Lines that couldn't be sent last time (no internet) go first.
        runCatching { file?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } }.getOrNull()?.let {
            synchronized(lines) { lines.addAll(0, it.takeLast(MAX_LINES)) }
        }
        log(
            "app.start",
            "version" to BuildConfig.VERSION_NAME, "code" to BuildConfig.VERSION_CODE,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "android" to Build.VERSION.SDK_INT,
        )
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(12_000) // ntfy.sh allows a request every few seconds
                flush()
            }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                error("crash", e, "thread" to thread.name)
                persist()
                Thread { flush() }.apply { start(); join(2_000) }
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun log(event: String, vararg fields: Pair<String, Any?>) {
        Log.d(TAG, event + " " + fields.joinToString(" ") { "${it.first}=${it.second}" })
        if (!enabled()) return
        val line = buildJsonObject {
            put("t", System.currentTimeMillis())
            put("s", session)
            put("e", event)
            for ((k, v) in fields) when (v) {
                null -> put(k, JsonNull)
                is Number -> put(k, v)
                is Boolean -> put(k, v)
                else -> put(k, Discovery.mask(v.toString()).take(900))
            }
        }.toString()
        synchronized(lines) {
            lines += line
            while (lines.size > MAX_LINES) lines.removeAt(0)
        }
    }

    fun error(event: String, e: Throwable, vararg fields: Pair<String, Any?>) = log(
        event, *fields,
        "error" to e.toString(),
        "at" to e.stackTrace.take(8).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" },
    )

    /** Keeps what's waiting on disk (the app may be closed before it gets online). */
    fun persist() {
        val f = file ?: return
        val copy = synchronized(lines) { lines.toList() }
        runCatching { f.writeText(copy.joinToString("\n")) }
    }

    /** Posts what's waiting (blocking; call off the main thread). */
    fun flush() {
        val client = http ?: return
        val batch = synchronized(lines) { lines.toList() }
        if (batch.isEmpty()) {
            file?.delete()
            return
        }
        val chunks = mutableListOf<List<String>>()
        var chunk = ArrayList<String>()
        var bytes = 0
        for (line in batch) {
            val size = line.toByteArray(Charsets.UTF_8).size + 1
            if (chunk.isNotEmpty() && bytes + size > MAX_BYTES) {
                chunks += chunk
                chunk = ArrayList()
                bytes = 0
            }
            chunk += line
            bytes += size
        }
        if (chunk.isNotEmpty()) chunks += chunk
        var sent = 0
        for (c in chunks) {
            val ok = runCatching {
                client.newCall(Request.Builder().url(URL).post(c.joinToString("\n").toRequestBody()).build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (!ok) break
            sent += c.size
        }
        if (sent > 0) synchronized(lines) { repeat(minOf(sent, lines.size)) { lines.removeAt(0) } }
        persist()
    }
}
