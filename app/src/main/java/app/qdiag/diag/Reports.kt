package app.qdiag.diag

import android.content.Context
import android.util.Log
import app.qdiag.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest

/**
 * Readings for the owner's car logbook: one compact JSON message per code reading, drive and
 * survey, posted to a ntfy.sh topic that a scheduled Claude task polls to update the logbook.
 *
 * The topic is derived from the car's VIN (`qdiag-car-` + the first 24 hex digits of
 * SHA-256("qdiag-car:" + VIN)), so it is in neither this public repository nor the APK: only
 * someone who knows the VIN can find it. Until the VIN has been read once, readings go to the
 * diagnostics topic instead (event "report"). Messages wait in a file until a post gets through
 * (the phone may be on the adapter's Wi-Fi), and each stays under ntfy's 4 KB text limit.
 */
object Reports {
    private const val TAG = "Reports"
    private const val MAX_BYTES = 3_800
    private const val MAX_QUEUED = 300

    private val queue = ArrayList<String>()
    private var file: File? = null
    private var prefs: android.content.SharedPreferences? = null
    @Volatile private var http: OkHttpClient? = null
    @Volatile private var enabled: () -> Boolean = { false }

    /** The car's topic URL once the VIN is known (kept across launches). */
    val topicUrl: String? get() = prefs?.getString("topic", null)?.let { "https://ntfy.sh/$it" }
    val pending: Int get() = synchronized(queue) { queue.size }

    fun start(context: Context, client: OkHttpClient, scope: CoroutineScope, isEnabled: () -> Boolean) {
        http = client
        enabled = isEnabled
        prefs = context.getSharedPreferences("qdiag_reports", Context.MODE_PRIVATE)
        file = File(context.filesDir, "reports-queue.jsonl")
        runCatching { file?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } }.getOrNull()?.let {
            synchronized(queue) { queue.addAll(0, it.takeLast(MAX_QUEUED)) }
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                flush()
                delay(20_000)
            }
        }
    }

    fun topicFor(vin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(("qdiag-car:" + vin.trim().uppercase()).toByteArray(Charsets.UTF_8))
        return "qdiag-car-" + digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    /** Called when the car's VIN is read: from now on readings go to the car's own topic. */
    fun setVin(vin: String) {
        if (vin.trim().length != 17) return
        prefs?.edit()?.putString("topic", topicFor(vin))?.apply()
    }

    /**
     * Queues one reading. [build] adds the fields; [shrink] is called with a smaller budget level
     * (1, 2, 3…) when the message is over ntfy's limit, to leave out detail.
     */
    fun send(kind: String, build: JsonObjectBuilder.(level: Int) -> Unit) {
        if (!enabled()) return
        var line = ""
        for (level in 0..4) {
            line = make(kind, level, build).toString()
            if (line.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) break
        }
        if (line.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            line = buildJsonObject { header(kind); put("truncated", true) }.toString()
        }
        Log.d(TAG, "queued $kind (${line.length} chars)")
        synchronized(queue) {
            queue += line
            while (queue.size > MAX_QUEUED) queue.removeAt(0)
        }
        persist()
    }

    private fun make(kind: String, level: Int, build: JsonObjectBuilder.(Int) -> Unit): JsonObject = buildJsonObject {
        header(kind)
        build(level)
    }

    private fun JsonObjectBuilder.header(kind: String) {
        put("qdiag", 1)
        put("kind", kind)
        put("t", System.currentTimeMillis())
        put("s", Diag.session)
        put("v", BuildConfig.VERSION_NAME)
    }

    private fun persist() {
        val f = file ?: return
        val copy = synchronized(queue) { queue.toList() }
        runCatching { f.writeText(copy.joinToString("\n")) }
    }

    /** Posts what's waiting, one message per reading (blocking; call off the main thread). */
    @Synchronized
    fun flush() {
        val client = http ?: return
        val url = topicUrl
        while (true) {
            val line = synchronized(queue) { queue.firstOrNull() } ?: break
            val ok = if (url != null) {
                post(client, url, line)
            } else {
                // No VIN yet: the diagnostics topic, as one event the logbook task also reads.
                post(client, Diag.URL, line.replaceFirst("{", "{\"e\":\"report\","))
            }
            if (!ok) break
            synchronized(queue) { if (queue.firstOrNull() == line) queue.removeAt(0) }
            persist()
        }
    }

    private fun post(client: OkHttpClient, url: String, body: String): Boolean = runCatching {
        client.newCall(Request.Builder().url(url).post(body.toRequestBody()).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
