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
import app.qdiag.diag.Reports
import app.qdiag.core.kb.Dtcs
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import app.qdiag.Graph
import app.qdiag.tr
import app.qdiag.link.SocketTransport
import app.qdiag.link.WifiLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    /** The owner wants the link up: a dropped link (adapter rebooting on engine start) is retried. */
    @Volatile private var wantLink = false
    /** Last exchange with the adapter: the link dies when it stays quiet (see [keepAlive]). */
    @Volatile private var lastTraffic = 0L
    private var keepAliveJob: Job? = null
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
        lastTraffic = System.currentTimeMillis()
        _console.update { (it + e).takeLast(600) }
    }

    fun connect() {
        if (_link.value.state == LinkState.CONNECTING || _link.value.state == LinkState.JOINING) return
        wantLink = true
        reconnectJob?.cancel()
        connectJob = scope.launch {
            _link.value = LinkStatus(LinkState.CONNECTING)
            if (open(firstTime = true)) {
                readVehicle()
                readCodes()
            }
        }
    }

    /**
     * Opens the socket and initialises the adapter. [firstTime]: may show Android's "join the
     * adapter's Wi-Fi" prompt and reports failures; a reconnection only tries the Wi-Fi the phone
     * is already on and stays quiet. True when the car answers.
     */
    private suspend fun open(firstTime: Boolean): Boolean {
        val s = settings.current
        Diag.log(if (firstTime) "link.connect" else "link.reconnect", "host" to s.host, "port" to s.port, "ssid" to s.ssidPrefix)
        try {
            val socket = wifi.open(s.host, s.port, if (firstTime) s.ssidPrefix else "") { _link.value = LinkStatus(LinkState.JOINING) }
            if (firstTime) _link.value = LinkStatus(LinkState.CONNECTING, "adapter")
            val t = SocketTransport(socket)
            transport = t
            val c = ObdClient(t, ::onExchange)
            client = c
            val init = mutex.withLock { withContext(Dispatchers.IO) { c.init(s.protocol.ifBlank { null }) } }
            Diag.log(
                "elm.init", "adapter" to init.adapter, "protocol" to init.protocol, "headers" to init.headers,
                "connected" to init.connected, "volts" to init.voltage, "problem" to init.problem?.name, "first" to firstTime,
            )
            if (firstTime) {
                _info.value = CarInfo(adapter = init.adapter, protocol = init.protocol, headers = init.headers, voltage = init.voltage)
            } else {
                _info.update { it.copy(voltage = init.voltage ?: it.voltage) }
            }
            if (!init.connected) {
                close()
                if (firstTime) _link.value = LinkStatus(LinkState.FAILED, init.problem?.name ?: "NO_ECU")
                return false
            }
            init.protocol?.let { p -> settings.update { it.copy(protocol = p) } }
            _link.value = LinkStatus(LinkState.READY)
            wifi.watch {
                // Android dropped the adapter's Wi-Fi: close now so the next command reconnects.
                Diag.log("link.wifiLost")
                transport?.close()
            }
            keepAlive()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diag.error(if (firstTime) "link.error" else "link.retry", e)
            close()
            if (firstTime) _link.value = LinkStatus(LinkState.FAILED, if (e is IOException) "WIFI" else e.javaClass.simpleName)
            return false
        }
    }

    /**
     * The adapter dropped the link, usually because starting the engine made the voltage dip and
     * it rebooted: wait for its Wi-Fi to come back and reconnect, for about a minute.
     */
    private fun reconnect() {
        if (reconnectJob?.isActive == true) return
        _link.value = LinkStatus(LinkState.CONNECTING, "reconnect")
        reconnectJob = scope.launch {
            for (attempt in 1..15) {
                delay(if (attempt == 1) 2_500 else 4_000)
                if (!wantLink) return@launch
                if (open(firstTime = false)) {
                    Diag.log("link.back", "attempt" to attempt)
                    if (_info.value.supported.isEmpty()) readVehicle()
                    return@launch
                }
                _link.value = LinkStatus(LinkState.CONNECTING, "reconnect")
            }
            Diag.log("link.gaveUp")
            _link.value = LinkStatus(LinkState.FAILED, "LOST")
        }
    }

    /**
     * The first sessions lost the link after 30 s to 2 min without traffic (between reading the
     * codes and starting live data). While nothing else talks to the adapter, ask it for the
     * battery voltage every few seconds: it keeps the link busy and the voltage on screen fresh.
     */
    private fun keepAlive() {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob = scope.launch {
            while (isActive && wantLink) {
                delay(2_000)
                if (!connected || liveJob?.isActive == true || _busy.value != null || mutex.isLocked) continue
                if (System.currentTimeMillis() - lastTraffic < 4_000) continue
                elm { it.voltage() }?.let { v -> _info.update { it.copy(voltage = v) } }
            }
        }
    }

    fun disconnect() {
        wantLink = false
        keepAliveJob?.cancel()
        keepAliveJob = null
        connectJob?.cancel()
        connectJob = null
        reconnectJob?.cancel()
        reconnectJob = null
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
            if (wantLink) reconnect() else _link.value = LinkStatus(LinkState.FAILED, "LOST")
            null
        } catch (e: Exception) {
            // A reply the parser didn't expect: keep going, and tell the developer.
            Diag.error("elm.error", e)
            null
        }
    }

    private suspend fun readVehicle() {
        val supported = elm { it.supportedPids() } ?: return
        val vin = elm { it.vin() }
        val name = elm { it.info(0x0A) }
        val cal = elm { it.info(0x04) }
        _info.update { it.copy(supported = supported, vin = vin, ecuName = name, calibration = cal) }
        vin?.let { Reports.setVin(it) }
        Diag.log(
            "car.info", "supported" to supported.sorted().joinToString(" ") { "%02X".format(it) },
            "vin" to vin, "ecu" to name, "cal" to cal,
            "unknownPids" to supported.filter { Pids[it] == null && it !in setOf(0x01, 0x03, 0x13, 0x1C) }.joinToString(" ") { "%02X".format(it) },
        )
    }

    fun readCodes() {
        scope.launch {
            _codes.update { it.copy(busy = true) }
            val stored = elm { it.dtcs(0x03) }
            if (stored == null) {
                // Link lost or the ECU refused: keep the previous reading.
                _codes.update { it.copy(busy = false) }
                Diag.log("codes.fail")
                if (connected) Graph.toast(tr("Couldn't read the codes", "Lecture des codes impossible"))
                return@launch
            }
            val pending = elm { it.dtcs(0x07) }
            val permanent = elm { it.dtcs(0x0A) }
            val monitors = elm { it.monitorStatus() }
            val supported = _info.value.supported
            suspend fun counter(pid: Int): Double? = if (pid in supported) elm { it.read(pid) }?.firstOrNull()?.value else null
            val dist = counter(0x31)
            val distMil = counter(0x21)
            val warmups = counter(0x30)
            val minutesSinceClear = counter(0x4E)
            val volts = elm { it.voltage() }
            val now = System.currentTimeMillis()
            _codes.value = CodesState(stored, pending.orEmpty(), permanent.orEmpty(), monitors, dist, now, busy = false)
            val c = _codes.value
            store.addRead(CodeRead(now, c.storedList, c.pendingList, c.permanentList))
            Diag.log(
                "codes.read", "stored" to c.stored.toString(), "pending" to c.pending.toString(), "permanent" to c.permanent.toString(),
                "mil" to monitors?.milOn, "count" to monitors?.dtcCount,
                "incomplete" to monitors?.incomplete?.joinToString(",") { it.name.en }, "distClear" to dist,
            )
            val info = _info.value
            Reports.send("codes") { level ->
                putJsonArray("stored") { c.storedList.forEach { add(it) } }
                putJsonArray("pending") { c.pendingList.forEach { add(it) } }
                putJsonArray("permanent") { c.permanentList.forEach { add(it) } }
                putJsonObject("about") {
                    for (code in (c.storedList + c.pendingList + c.permanentList).distinct()) {
                        val d = Dtcs.info(code)
                        putJsonObject(code) {
                            put("fr", d.title.fr)
                            put("severity", d.severity.name)
                            put("known", d.known)
                            if (level < 2) putJsonArray("parts") { d.parts.forEach { add(it) } }
                        }
                    }
                }
                monitors?.let { m ->
                    put("mil", m.milOn)
                    put("dtcCount", m.dtcCount)
                    putJsonArray("incomplete") { m.incomplete.forEach { add(it.name.fr) } }
                }
                dist?.let { put("kmSinceClear", it) }
                distMil?.let { put("kmWithMil", it) }
                warmups?.let { put("warmupsSinceClear", it) }
                minutesSinceClear?.let { put("minutesSinceClear", it) }
                (volts ?: info.voltage)?.let { put("batteryV", it) }
                info.ecuName?.let { put("ecu", it) }
                info.calibration?.let { put("calibration", it) }
                info.protocol?.let { put("protocol", it) }
                if (level < 1) put("supportedPids", info.supported.sorted().joinToString(" ") { "%02X".format(it) })
            }
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
            while (isActive) {
                if (!connected) {
                    // Reconnecting (adapter rebooted): keep the session and carry on once it's back.
                    if (reconnectJob?.isActive == true) {
                        delay(500)
                        continue
                    }
                    break
                }
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
                if (!connected) continue
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
            // The link didn't come back: end the session (and keep its check-up) rather than
            // showing it as running with nothing being read.
            if (isActive) {
                Diag.log("live.ended", "why" to "link")
                finishLive()
            }
        }
    }

    fun stopLive() {
        val job = liveJob ?: run {
            // A session left marked as running (link lost before 0.2.2): just clear it.
            if (_live.value.running) _live.update { it.copy(running = false) }
            return
        }
        job.cancel()
        finishLive()
    }

    private fun finishLive() {
        liveJob = null
        if (!_live.value.running) return
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
            reportDrive(started, minutes, findings)
        }
    }

    /** Keys worth summarising first when the drive report must be short. */
    private val mainKeys = listOf(
        "coolant", "rpm", "speed", "load", "pedal", "battV", "ecuV", "map", "boost", "boostCmd", "rail", "railCmd", "railAct",
        "maf", "egrCmd", "egrErr", "egrA", "egrACmd", "dpfDp", "dpfTin", "egt1", "iat", "oil", "fuelRate", "lambda1", "baro",
    )

    private fun reportDrive(started: Long, minutes: Double, findings: List<Finding>) {
        val samples = recorded.toList()
        var km = 0.0
        for (i in 1 until samples.size) {
            val v = samples[i].values["speed"] ?: continue
            val dt = ((samples[i].t - samples[i - 1].t) / 1000.0).coerceIn(0.0, 10.0)
            km += v * dt / 3600.0
        }
        val keys = samples.flatMap { it.values.keys }.distinct()
        Reports.send("drive") { level ->
            put("start", started)
            put("minutes", Math.round(minutes * 10) / 10.0)
            put("samples", samples.size)
            put("km", Math.round(km * 10) / 10.0)
            putJsonArray("findings") {
                for (f in findings) add(
                    buildJsonObject {
                        put("id", f.id)
                        put("level", f.level.name)
                        put("fr", f.title.fr)
                        if (level < 3 && f.level != app.qdiag.core.health.Level.GOOD) put("detail", f.detail.fr)
                        if (f.parts.isNotEmpty()) putJsonArray("parts") { f.parts.forEach { add(it) } }
                    },
                )
            }
            val statKeys = when (level) {
                0 -> mainKeys.filter { it in keys } + (keys - mainKeys.toSet())
                1 -> mainKeys.filter { it in keys }
                2 -> mainKeys.take(12).filter { it in keys }
                else -> emptyList()
            }
            if (statKeys.isNotEmpty()) putJsonObject("stats") {
                for (k in statKeys) {
                    val v = samples.mapNotNull { it.values[k] }.sorted()
                    if (v.isEmpty()) continue
                    putJsonArray(k) {
                        add(round3(v.first()))
                        add(round3(v[v.size / 2]))
                        add(round3(v.last()))
                    }
                }
            }
        }
    }

    private fun round3(x: Double): Double = Math.round(x * 1000) / 1000.0

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
            val answered = ArrayList<Triple<String, String, String>>()
            var asked = 0
            for (p in Discovery.all) {
                val reply = elm { it.send(p.command, p.timeoutMs) } ?: break
                asked++
                val text = reply.replace('\r', ' ').replace(">", "").trim()
                val shown = if (p.sensitive) "masked (${text.length} chars)" else text
                Diag.log("discovery", "cmd" to p.command, "what" to p.label, "reply" to shown)
                val refused = text.isEmpty() || listOf("NO DATA", "?", "ERROR", "UNABLE", "STOPPED", "7F ").any { text.contains(it) } || text == "OK"
                if (!p.command.startsWith("AT") && !refused) answered += Triple(p.command, p.label, shown)
            }
            val unknownPids = ArrayList<Pair<String, String>>()
            // Every supported PID once, raw: PIDs the app doesn't decode yet show up here.
            for (pid in _info.value.supported.sorted()) {
                val reply = elm { it.request("01%02X".format(pid)) } ?: break
                val raw = (reply as? ElmReply.Data)?.payload(0x41, pid)?.let { Obd.hex(it) } ?: reply.raw.joinToString(" | ")
                Diag.log("discovery.pid", "pid" to "%02X".format(pid), "raw" to raw, "known" to (Pids[pid] != null))
                if (Pids[pid] == null) unknownPids += "%02X".format(pid) to raw
            }
            Diag.log("discovery.end")
            Reports.send("survey") { level ->
                put("asked", asked)
                put("answeredCount", answered.size)
                val cut = when (level) { 0 -> 120; 1 -> 60; 2 -> 30; else -> 0 }
                if (cut > 0) putJsonArray("answered") {
                    for ((cmd, label, reply) in answered) add(
                        buildJsonObject {
                            put("cmd", cmd)
                            put("what", label)
                            put("reply", reply.take(cut))
                        },
                    )
                }
                if (level < 4) putJsonObject("unknownPids") { for ((pid, raw) in unknownPids) put(pid, raw.take(60)) }
            }
            _busy.value = null
            scope.launch(Dispatchers.IO) { Diag.flush() }
        }
    }

    fun clearConsole() {
        _console.value = emptyList()
    }
}
