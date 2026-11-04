package app.qdiag.car

import android.content.Context
import app.qdiag.core.elm.Discovery
import app.qdiag.core.elm.ElmReply
import app.qdiag.core.elm.Exchange
import app.qdiag.core.elm.ObdClient
import app.qdiag.core.health.Analyzer
import app.qdiag.core.health.Finding
import app.qdiag.core.health.Readiness
import app.qdiag.core.health.Sample
import app.qdiag.core.obd.MonitorStatus
import app.qdiag.core.obd.Obd
import app.qdiag.core.obd.PidDef
import app.qdiag.core.obd.PidGroup
import app.qdiag.core.obd.Pids
import app.qdiag.core.obd.Signal
import app.qdiag.data.CodeRead
import app.qdiag.data.DriveSummary
import app.qdiag.data.Settings
import app.qdiag.data.Store
import app.qdiag.diag.Diag
import app.qdiag.link.SocketTransport
import app.qdiag.link.WifiLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class LinkState { IDLE, JOINING, CONNECTING, READY, FAILED }

data class LinkStatus(
    val state: LinkState = LinkState.IDLE,
    /** Why it failed, or what it's doing (key the UI turns into words). */
    val detail: String? = null,
)

data class CarInfo(
    val adapter: String? = null,
    val protocol: String? = null,
    val headers: Boolean = true,
    val voltage: Double? = null,
    val vin: String? = null,
    val ecuName: String? = null,
    val calibration: String? = null,
    val supported: Set<Int> = emptySet(),
)

data class CodesState(
    val stored: Map<String, List<String>> = emptyMap(),
    val pending: Map<String, List<String>> = emptyMap(),
    val permanent: Map<String, List<String>> = emptyMap(),
    val monitors: MonitorStatus? = null,
    val distanceSinceClear: Double? = null,
    val readAt: Long? = null,
    val busy: Boolean = false,
) {
    val storedList: List<String> get() = stored.values.flatten().distinct()
    val pendingList: List<String> get() = pending.values.flatten().distinct()
    val permanentList: List<String> get() = permanent.values.flatten().distinct()
}

data class LiveState(
    val running: Boolean = false,
    val values: Map<String, Signal> = emptyMap(),
    /** Which group each value's PID belongs to, for the Live screen's sections. */
    val groups: Map<String, PidGroup> = emptyMap(),
    val battery: Double? = null,
    val samples: Int = 0,
    val startedAt: Long? = null,
    /** Full refreshes of the fast values per second. */
    val rate: Double = 0.0,
)

/**
 * Everything the app does with the car, over one ELM327 link at a time. Commands go through a
 * mutex so live polling, code reads and the console take turns on the adapter.
 */
class CarSession(
    private val context: Context,
    private val settings: Settings,
    private val store: Store,
    private val scope: CoroutineScope,
) {
    private val wifi = WifiLink(context)
    private val mutex = Mutex()
    @Volatile private var transport: SocketTransport? = null
    @Volatile private var client: ObdClient? = null
    private var liveJob: Job? = null
    private var analyzer = Analyzer()
    private val recorded = ArrayList<Sample>()

    private val _link = MutableStateFlow(LinkStatus())
    val link: StateFlow<LinkStatus> = _link.asStateFlow()
    private val _info = MutableStateFlow(CarInfo())
    val info: StateFlow<CarInfo> = _info.asStateFlow()
    private val _codes = MutableStateFlow(CodesState())
    val codes: StateFlow<CodesState> = _codes.asStateFlow()
    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()
    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    /** Health checks of the current (or last) live session. */
    val findings: StateFlow<List<Finding>> = _findings.asStateFlow()
    private val _console = MutableStateFlow<List<Exchange>>(emptyList())
    val console: StateFlow<List<Exchange>> = _console.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    /** A long task in progress (discovery), for the console. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    val connected: Boolean get() = _link.value.state == LinkState.READY

    private fun onExchange(e: Exchange) {
        _console.update { (it + e).takeLast(600) }
    }

    fun connect() {
        if (_link.value.state == LinkState.CONNECTING || _link.value.state == LinkState.JOINING) return
        scope.launch {
            val s = settings.current
            _link.value = LinkStatus(LinkState.CONNECTING)
            Diag.log("link.connect", "host" to s.host, "port" to s.port, "ssid" to s.ssidPrefix)
            try {
                val socket = wifi.open(s.host, s.port, s.ssidPrefix) { _link.value = LinkStatus(LinkState.JOINING) }
                _link.value = LinkStatus(LinkState.CONNECTING, "adapter")
                val t = SocketTransport(socket)
                transport = t
                val c = ObdClient(t, ::onExchange)
                client = c
                val init = mutex.withLock { withContext(Dispatchers.IO) { c.init(s.protocol.ifBlank { null }) } }
                Diag.log(
                    "elm.init", "adapter" to init.adapter, "protocol" to init.protocol, "headers" to init.headers,
                    "connected" to init.connected, "volts" to init.voltage, "problem" to init.problem?.name,
                )
                _info.value = CarInfo(adapter = init.adapter, protocol = init.protocol, headers = init.headers, voltage = init.voltage)
                if (!init.connected) {
                    _link.value = LinkStatus(LinkState.FAILED, init.problem?.name ?: "NO_ECU")
                    return@launch
                }
                init.protocol?.let { p -> settings.update { it.copy(protocol = p) } }
                _link.value = LinkStatus(LinkState.READY)
                readVehicle()
                readCodes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diag.error("link.error", e)
                close()
                _link.value = LinkStatus(LinkState.FAILED, if (e is IOException) "WIFI" else e.javaClass.simpleName)
            }
        }
    }

    fun disconnect() {
        stopLive()
        close()
        _link.value = LinkStatus(LinkState.IDLE)
        Diag.log("link.disconnect")
        scope.launch(Dispatchers.IO) { Diag.flush() }
    }

    private fun close() {
        val t = transport
        transport = null
        client = null
        if (t != null) scope.launch(Dispatchers.IO) { t.close() }
        wifi.release()
    }

    /** Runs [block] on the adapter, turning a dropped link into a FAILED state. */
    private suspend fun <T> elm(block: (ObdClient) -> T): T? {
        val c = client ?: return null
        return try {
            mutex.withLock { withContext(Dispatchers.IO) { block(c) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Closed on purpose (Disconnect) while a command was waiting: not a lost link.
            if (client !== c) return null
            Diag.error("link.lost", e)
            close()
            _link.value = LinkStatus(LinkState.FAILED, "LOST")
            null
        }
    }

    private suspend fun readVehicle() {
        val supported = elm { it.supportedPids() } ?: return
        val vin = elm { it.vin() }
        val name = elm { it.info(0x0A) }
        val cal = elm { it.info(0x04) }
        _info.update { it.copy(supported = supported, vin = vin, ecuName = name, calibration = cal) }
        Diag.log(
            "car.info", "supported" to supported.sorted().joinToString(" ") { "%02X".format(it) },
            "vin" to vin, "ecu" to name, "cal" to cal,
            "unknownPids" to supported.filter { Pids[it] == null && it !in setOf(0x01, 0x03, 0x1C) }.joinToString(" ") { "%02X".format(it) },
        )
    }

    fun readCodes() {
        scope.launch {
            _codes.update { it.copy(busy = true) }
            val stored = elm { it.dtcs(0x03) }
            val pending = elm { it.dtcs(0x07) }
            val permanent = elm { it.dtcs(0x0A) }
            val monitors = elm { it.monitorStatus() }
            val dist = if (0x31 in _info.value.supported) elm { it.read(0x31) }?.firstOrNull()?.value else null
            val now = System.currentTimeMillis()
            _codes.value = CodesState(stored.orEmpty(), pending.orEmpty(), permanent.orEmpty(), monitors, dist, now, busy = false)
            val c = _codes.value
            store.addRead(CodeRead(now, c.storedList, c.pendingList, c.permanentList))
            Diag.log(
                "codes.read", "stored" to c.stored.toString(), "pending" to c.pending.toString(), "permanent" to c.permanent.toString(),
                "mil" to monitors?.milOn, "count" to monitors?.dtcCount,
                "incomplete" to monitors?.incomplete?.joinToString(",") { it.name.en }, "distClear" to dist,
            )
        }
    }

    fun clearCodes() {
        scope.launch {
            val before = _codes.value.storedList
            val ok = elm { it.clearDtcs() } ?: false
            Diag.log("codes.clear", "ok" to ok, "before" to before.joinToString(","))
            if (ok) store.addRead(CodeRead(System.currentTimeMillis(), cleared = true))
            readCodes()
        }
    }

    /** Readiness for the contrôle technique, from the last code read. */
    fun readiness(): List<Finding> {
        val c = _codes.value
        if (c.readAt == null) return emptyList()
        return Readiness.check(c.monitors, c.storedList, c.pendingList, c.distanceSinceClear)
    }

    // ---- Live data -------------------------------------------------------------------------

    private val fastPids = setOf(0x0C, 0x0D, 0x04, 0x49, 0x0B, 0x70, 0x87, 0x10, 0x23, 0x6D, 0x2C, 0x2D, 0x69, 0x7A)

    fun startLive() {
        if (liveJob?.isActive == true || !connected) return
        analyzer = Analyzer()
        recorded.clear()
        _findings.value = emptyList()
        val supported = _info.value.supported
        val defs: List<PidDef> = Pids.all.filter { it.pid in supported && it.pid !in setOf(0x21, 0x30, 0x31, 0x4D, 0x4E) }
        val fast = defs.filter { it.pid in fastPids }
        val slow = defs.filter { it.pid !in fastPids }
        val started = System.currentTimeMillis()
        _live.value = LiveState(running = true, startedAt = started)
        Diag.log("live.start", "fast" to fast.joinToString(" ") { it.hex }, "slow" to slow.joinToString(" ") { it.hex })
        liveJob = scope.launch {
            var cycle = 0
            var lastLog = 0L
            val values = LinkedHashMap<String, Signal>()
            val groups = HashMap<String, PidGroup>()
            var battery: Double? = null
            while (isActive && connected) {
                val cycleStart = System.currentTimeMillis()
                val batch = if (cycle % 5 == 0) fast + slow else fast
                for (def in batch) {
                    val signals = elm { it.read(def.pid) } ?: if (!connected) break else continue
                    for (s in signals) {
                        values[s.key] = s
                        groups[s.key] = def.group
                    }
                }
                if (cycle % 8 == 0) elm { it.voltage() }?.let { battery = it }
                if (!connected) break
                val now = System.currentTimeMillis()
                val sampleValues = values.mapValues { it.value.value }.toMutableMap()
                battery?.let { sampleValues["battV"] = it }
                val sample = Sample(now, sampleValues)
                analyzer.add(sample)
                if (recorded.size < 30_000) recorded += sample
                val rate = 1000.0 / (now - cycleStart).coerceAtLeast(1)
                _live.update { it.copy(values = LinkedHashMap(values), groups = HashMap(groups), battery = battery, samples = analyzer.size, rate = rate) }
                if (cycle % 10 == 0) _findings.value = analyzer.findings()
                if (now - lastLog >= 5_000) {
                    lastLog = now
                    Diag.log("live", *sampleValues.entries.map { it.key to "%.2f".format(it.value) }.toTypedArray())
                }
                cycle++
            }
        }
    }

    fun stopLive() {
        val job = liveJob ?: return
        job.cancel()
        liveJob = null
        val started = _live.value.startedAt ?: System.currentTimeMillis()
        _live.update { it.copy(running = false) }
        val findings = analyzer.findings()
        _findings.value = findings
        if (analyzer.size > 0) {
            val minutes = (System.currentTimeMillis() - started) / 60_000.0
            store.addDrive(
                DriveSummary(
                    started, minutes, analyzer.size,
                    findings.associate { it.id to it.level.name },
                    findings.associate { it.id to it.detail.en },
                ),
            )
            Diag.log("live.stop", "minutes" to "%.1f".format(minutes), "samples" to analyzer.size, *findings.map { it.id to "${it.level}: ${it.detail.en}" }.toTypedArray())
        }
    }

    /** The current (or last) live session as CSV, for sharing. */
    fun exportCsv(): File? {
        if (recorded.isEmpty()) return null
        val keys = recorded.flatMap { it.values.keys }.distinct()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "qdiag-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(recorded.first().t)}.csv")
        file.bufferedWriter().use { w ->
            w.write("time_s," + keys.joinToString(","))
            w.newLine()
            val t0 = recorded.first().t
            for (s in recorded) {
                w.write("%.2f".format(java.util.Locale.ROOT, (s.t - t0) / 1000.0))
                for (k in keys) w.write("," + (s.values[k]?.let { "%.3f".format(java.util.Locale.ROOT, it) } ?: ""))
                w.newLine()
            }
        }
        return file
    }

    // ---- Console and discovery -------------------------------------------------------------

    /**
     * Requests that change something in the car: clearing codes outside the Codes screen, UDS /
     * KWP sessions, resets, writes, routines, actuator tests, security access, downloads.
     */
    private val writeServices = setOf(0x04, 0x08, 0x10, 0x11, 0x14, 0x27, 0x28, 0x2E, 0x2F, 0x30, 0x31, 0x34, 0x35, 0x36, 0x37, 0x3B, 0x3D, 0x83, 0x84, 0x85, 0x87)

    /** Null when sent; otherwise why it was refused. */
    fun send(command: String): String? {
        val cmd = command.trim().uppercase().replace(" ", "")
        if (cmd.isEmpty()) return null
        if (!connected) return "OFFLINE"
        if (!cmd.startsWith("AT") && !settings.current.expertConsole) {
            val service = runCatching { cmd.substring(0, 2).toInt(16) }.getOrNull() ?: return "HEX"
            if (service in writeServices) return "WRITE"
        }
        scope.launch {
            val reply = elm { it.send(cmd, 4_000) }
            Diag.log("console", "cmd" to cmd, "reply" to reply?.trim())
        }
        return null
    }

    fun runDiscovery() {
        if (_busy.value != null || !connected) return
        scope.launch {
            _busy.value = "discovery"
            Diag.log("discovery.start")
            for (p in Discovery.all) {
                val reply = elm { it.send(p.command, p.timeoutMs) } ?: break
                val text = reply.replace('\r', ' ').replace(">", "").trim()
                val shown = if (p.sensitive) "masked (${text.length} chars)" else text
                Diag.log("discovery", "cmd" to p.command, "what" to p.label, "reply" to shown)
            }
            // Every supported PID once, raw: PIDs the app doesn't decode yet show up here.
            for (pid in _info.value.supported.sorted()) {
                val reply = elm { it.request("01%02X".format(pid)) } ?: break
                val raw = (reply as? ElmReply.Data)?.payload(0x41, pid)?.let { Obd.hex(it) } ?: reply.raw.joinToString(" | ")
                Diag.log("discovery.pid", "pid" to "%02X".format(pid), "raw" to raw, "known" to (Pids[pid] != null))
            }
            Diag.log("discovery.end")
            _busy.value = null
            scope.launch(Dispatchers.IO) { Diag.flush() }
        }
    }

    fun clearConsole() {
        _console.value = emptyList()
    }
}
