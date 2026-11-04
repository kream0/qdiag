package app.qdiag.core.elm

/** Turns the text an ELM327 sends back (up to its `>` prompt) into an [ElmReply]. */
object ElmParser {

    private val HEX = Regex("^[0-9A-F]+$")
    private val NOISE = listOf("SEARCHING...", "SEARCHING", "BUS INIT: ...", "BUS INIT:...", "BUS INIT: OK", "BUS INIT:OK")

    /**
     * The meaningful lines of [raw]: carriage returns split, prompt and echo of [command]
     * dropped, `SEARCHING...` dropped, upper-cased.
     */
    fun lines(raw: String, command: String? = null): List<String> {
        val echo = command?.replace(" ", "")?.uppercase()
        return raw.replace('\r', '\n').split('\n')
            .map { it.replace(">", "").trim().uppercase() }
            .filter { it.isNotEmpty() }
            .map { line -> NOISE.fold(line) { acc, n -> if (acc.startsWith(n)) acc.removePrefix(n).trim() else acc } }
            .filter { it.isNotEmpty() }
            .filterNot { echo != null && it.replace(" ", "") == echo }
    }

    /** A reply to an AT command (anything that isn't OBD data): kept as text, errors recognised. */
    fun parseText(raw: String, command: String? = null): ElmReply {
        val lines = lines(raw, command)
        errorOf(lines)?.let { return ElmReply.Error(it, lines) }
        return ElmReply.Text(lines, lines)
    }

    /**
     * A reply to an OBD request ([command] is the request in hex, e.g. "010C"). Recognises the
     * ELM's error words first, then reads the frames with or without headers.
     */
    fun parseData(raw: String, command: String? = null): ElmReply {
        val lines = lines(raw, command)
        if (lines.isEmpty()) return ElmReply.Error(ElmReply.Kind.NO_DATA, lines)
        val dataLines = lines.filterNot { isNoise(it) }
        val error = errorOf(lines)
        val frames = dataLines.mapNotNull { frameOf(it) }
        if (frames.isEmpty()) return ElmReply.Error(error ?: ElmReply.Kind.GARBLED, lines)
        val byEcu = assemble(frames)
        if (byEcu.isEmpty()) return ElmReply.Error(error ?: ElmReply.Kind.GARBLED, lines)
        return ElmReply.Data(byEcu, lines)
    }

    private fun isNoise(line: String): Boolean =
        line.startsWith("<RX ERROR") || line.startsWith("<DATA ERROR") || line == "DATA ERROR"

    private fun errorOf(lines: List<String>): ElmReply.Kind? {
        for (l in lines) {
            when {
                l.contains("NO DATA") -> return ElmReply.Kind.NO_DATA
                l == "?" -> return ElmReply.Kind.UNKNOWN_COMMAND
                l.contains("UNABLE TO CONNECT") -> return ElmReply.Kind.UNABLE_TO_CONNECT
                l.contains("CAN ERROR") -> return ElmReply.Kind.CAN_ERROR
                l.contains("BUS ERROR") || l.contains("BUS BUSY") || l.contains("BUS INIT") && l.contains("ERROR") ||
                    l.startsWith("ERR") || l.contains("FB ERROR") || l.contains("LV RESET") -> return ElmReply.Kind.BUS_ERROR
                l.contains("STOPPED") -> return ElmReply.Kind.STOPPED
                l.contains("BUFFER FULL") -> return ElmReply.Kind.BUFFER_FULL
            }
        }
        return null
    }

    /** One line of data: its ECU address (or null without headers) and its bytes. */
    internal data class Frame(val ecu: String?, val bytes: IntArray, val index: Int? = null, val lengthOnly: Int? = null)

    internal fun frameOf(line: String): Frame? {
        // Header-less multi-frame: the total length on its own line ("014"), then "0: 49 02 …".
        if (line.length == 3 && HEX.matches(line)) return Frame(null, IntArray(0), lengthOnly = line.toInt(16))
        val indexed = Regex("^([0-9A-F]):\\s*(.*)$").find(line)
        if (indexed != null) {
            val bytes = hexBytes(indexed.groupValues[2]) ?: return null
            return Frame(null, bytes, index = indexed.groupValues[1].toInt(16))
        }
        val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.any { !HEX.matches(it) }) return null
        if (tokens.size == 1) {
            // Spaces off: "7E8064100BE3EA813" (11-bit header, 3 hex digits, then pairs).
            val t = tokens[0]
            return when {
                t.length % 2 == 1 && t.length >= 5 -> hexBytes(t.substring(3))?.let { Frame(t.substring(0, 3), it) }
                t.length % 2 == 0 -> hexBytes(t)?.let { Frame(null, it) }
                else -> null
            }
        }
        // 11-bit CAN header: "7E8 06 41 0C …".
        if (tokens[0].length == 3) {
            val bytes = hexBytes(tokens.drop(1).joinToString(" ")) ?: return null
            return Frame(tokens[0], bytes)
        }
        // 29-bit CAN header: "18 DA F1 10 06 41 0C …".
        if (tokens.size >= 5 && tokens[0] == "18" && (tokens[1] == "DA" || tokens[1] == "DB")) {
            val bytes = hexBytes(tokens.drop(4).joinToString(" ")) ?: return null
            return Frame(tokens.take(4).joinToString(""), bytes)
        }
        // No header: the ELM already removed the ISO-TP byte (CAN auto-formatting).
        return hexBytes(tokens.joinToString(" "))?.let { Frame(null, it) }
    }

    private fun hexBytes(s: String): IntArray? {
        val compact = s.replace(" ", "")
        if (compact.isEmpty() || compact.length % 2 != 0 || !HEX.matches(compact)) return null
        return IntArray(compact.length / 2) { compact.substring(it * 2, it * 2 + 2).toInt(16) }
    }

    /**
     * Reassembles each ECU's ISO-TP frames: single frame (0x0L), first frame (0x1L LL) then
     * consecutive frames (0x2N), flow control (0x3x) ignored. Without headers, lines are already
     * payloads (single) or indexed parts of one message.
     */
    internal fun assemble(frames: List<Frame>): Map<String, IntArray> {
        val out = LinkedHashMap<String, IntArray>()
        val withHeader = frames.filter { it.ecu != null }
        val grouped = withHeader.groupBy { it.ecu!! }
        for ((ecu, list) in grouped) {
            var expected = -1
            val buf = ArrayList<Int>()
            for (f in list) {
                if (f.bytes.isEmpty()) continue
                val pci = f.bytes[0]
                when (pci shr 4) {
                    0x0 -> {
                        val len = pci and 0x0F
                        val data = f.bytes.drop(1).take(len)
                        // Several single frames from one ECU (e.g. several DTC requests): keep the first.
                        if (!out.containsKey(ecu)) out[ecu] = data.toIntArray()
                    }
                    0x1 -> {
                        if (f.bytes.size < 2) continue
                        expected = ((pci and 0x0F) shl 8) or f.bytes[1]
                        buf.clear()
                        buf.addAll(f.bytes.drop(2))
                    }
                    0x2 -> if (expected >= 0) buf.addAll(f.bytes.drop(1))
                    else -> Unit
                }
            }
            if (expected >= 0 && buf.isNotEmpty()) out[ecu] = buf.take(expected).toIntArray()
        }
        val bare = frames.filter { it.ecu == null }
        if (bare.isNotEmpty() && out.isEmpty()) {
            val length = bare.firstNotNullOfOrNull { it.lengthOnly }
            val indexed = bare.filter { it.index != null }.sortedBy { it.index }
            if (indexed.isNotEmpty()) {
                val all = indexed.flatMap { it.bytes.toList() }
                out["?"] = (if (length != null) all.take(length) else all).toIntArray()
            } else {
                bare.filter { it.bytes.isNotEmpty() }.firstOrNull()?.let { out["?"] = it.bytes }
            }
        }
        return out
    }
}
