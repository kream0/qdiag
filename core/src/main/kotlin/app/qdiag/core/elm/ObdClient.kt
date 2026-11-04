package app.qdiag.core.elm

import app.qdiag.core.obd.MonitorStatus
import app.qdiag.core.obd.Obd
import app.qdiag.core.obd.Pids
import app.qdiag.core.obd.Signal

/** Sends one command to the adapter and returns everything it answered up to its `>` prompt. */
fun interface Transport {
    /** Blocking. Throws [java.io.IOException] when the link drops; returns what came if [timeoutMs] passes. */
    fun exchange(command: String, timeoutMs: Long): String
}

/** One command and its raw answer, for the console and the diagnostics. */
data class Exchange(val command: String, val reply: String, val ms: Long)

/** What [ObdClient.init] found out about the adapter and the car. */
data class InitResult(
    val adapter: String,
    val protocol: String?,
    val headers: Boolean,
    val connected: Boolean,
    val voltage: Double?,
    val problem: ElmReply.Kind? = null,
)

/**
 * The ELM327 conversation, blocking and single-threaded (the app runs it on one IO thread).
 * Every exchange goes through [listener], which the app shows in its console and logs.
 */
class ObdClient(
    private val transport: Transport,
    private val listener: (Exchange) -> Unit = {},
) {
    /** Whether replies carry CAN headers (`ATH1` accepted). */
    var headers = true
        private set

    fun send(command: String, timeoutMs: Long = 2_000): String {
        val start = System.nanoTime()
        val reply = transport.exchange(command, timeoutMs)
        listener(Exchange(command, reply.trim(), (System.nanoTime() - start) / 1_000_000))
        return reply
    }

    fun at(command: String, timeoutMs: Long = 1_500): ElmReply = ElmParser.parseText(send(command, timeoutMs), command)

    fun request(hex: String, timeoutMs: Long = 2_000): ElmReply = ElmParser.parseData(send(hex, timeoutMs), hex)

    /**
     * Resets the adapter and finds the car's protocol. [protocol] is the last one that worked
     * ("6" for ISO 15765-4 CAN 11-bit 500 kbit/s, the Qashqai's), tried before searching.
     */
    fun init(protocol: String? = null): InitResult {
        val reset = at("ATZ", 4_000)
        val adapter = (reset as? ElmReply.Text)?.lines?.firstOrNull { it.contains("ELM") }
            ?: (at("ATI") as? ElmReply.Text)?.first ?: "?"
        at("ATE0")
        at("ATL0")
        at("ATS1")
        headers = at("ATH1") !is ElmReply.Error
        at("ATAT1")
        val voltage = voltage()

        var problem: ElmReply.Kind? = null
        val candidates = listOfNotNull(protocol?.takeIf { it != "0" }, "0").distinct()
        for (p in candidates) {
            at("ATSP$p")
            val reply = request("0100", 10_000)
            if (reply is ElmReply.Data && reply.payload(0x41, 0x00) != null) {
                val dp = (at("ATDPN") as? ElmReply.Text)?.first?.removePrefix("A")
                return InitResult(adapter, dp ?: p, headers, connected = true, voltage = voltage)
            }
            problem = (reply as? ElmReply.Error)?.kind ?: ElmReply.Kind.GARBLED
        }
        return InitResult(adapter, null, headers, connected = false, voltage = voltage, problem = problem)
    }

    /** The adapter's reading of the car's battery (`ATRV`), in volts. */
    fun voltage(): Double? {
        val t = at("ATRV") as? ElmReply.Text ?: return null
        return t.first.removeSuffix("V").trim().replace(',', '.').toDoubleOrNull()
    }

    /** Mode 01 PIDs the engine ECU supports (01–C0), following the "next block" bits. */
    fun supportedPids(): Set<Int> {
        val out = LinkedHashSet<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val reply = request("01%02X".format(base)) as? ElmReply.Data ?: break
            val payload = reply.payload(0x41, base) ?: break
            val set = Obd.supportedPids(payload)
            out += set.filter { it % 0x20 != 0 }
            if ((base + 0x20) !in set) break
            base += 0x20
        }
        return out
    }

    /** One PID's decoded values, or null when the ECU doesn't answer it. */
    fun read(pid: Int): List<Signal>? {
        val reply = request("01%02X".format(pid)) as? ElmReply.Data ?: return null
        val payload = reply.payload(0x41, pid) ?: return null
        return Pids.decode(payload)
    }

    fun monitorStatus(): MonitorStatus? {
        val reply = request("0101") as? ElmReply.Data ?: return null
        return reply.payload(0x41, 0x01)?.let { MonitorStatus.parse(it) }
    }

    /**
     * Fault codes: mode 03 stored, 07 pending, 0A permanent. Codes from every ECU that answers
     * the broadcast, by ECU address.
     */
    fun dtcs(mode: Int): Map<String, List<String>>? {
        val reply = request("%02X".format(mode), 4_000)
        if (reply is ElmReply.Error) return if (reply.kind == ElmReply.Kind.NO_DATA) emptyMap() else null
        val data = reply as ElmReply.Data
        return data.payloads(mode + 0x40).mapValues { Obd.dtcs(it.value) }
    }

    /** Mode 04: clears stored codes and the light (and resets the readiness monitors). */
    fun clearDtcs(): Boolean {
        val reply = request("04", 5_000)
        return reply is ElmReply.Data && reply.payload(0x44) != null
    }

    fun vin(): String? {
        val reply = request("0902", 4_000) as? ElmReply.Data ?: return null
        return reply.payload(0x49, 0x02)?.let { Obd.vin(it) }
    }

    /** Mode 09 text items: calibration ID (04) and ECU name (0A). */
    fun info(pid: Int): String? {
        val reply = request("09%02X".format(pid), 4_000) as? ElmReply.Data ?: return null
        return reply.payload(0x49, pid)?.let { Obd.ascii(it) }?.takeIf { it.isNotBlank() }
    }
}
