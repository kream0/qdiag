package app.qdiag.core.obd

/** Small helpers for SAE J1979 (OBD-II / EOBD) replies. */
object Obd {

    /** "010C" → [0x01, 0x0C]. */
    fun request(hex: String): IntArray {
        val c = hex.replace(" ", "")
        require(c.length % 2 == 0) { "odd hex: $hex" }
        return IntArray(c.length / 2) { c.substring(it * 2, it * 2 + 2).toInt(16) }
    }

    fun hex(bytes: IntArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    /**
     * PIDs a supported-PIDs reply announces: [payload] is `41 <base> A B C D`; bit 7 of A is
     * PID base+1, bit 0 of D is base+32 (which says whether the next block exists).
     */
    fun supportedPids(payload: IntArray): Set<Int> {
        if (payload.size < 6) return emptySet()
        val base = payload[1]
        val out = LinkedHashSet<Int>()
        for (byte in 0 until 4) {
            val b = payload[2 + byte]
            for (bit in 0 until 8) {
                if (b and (0x80 shr bit) != 0) out += base + byte * 8 + bit + 1
            }
        }
        return out
    }

    /** "P0087" from the two bytes of a stored fault code; null for the 00 00 padding. */
    fun dtc(a: Int, b: Int): String? {
        if (a == 0 && b == 0) return null
        val letter = "PCBU"[(a shr 6) and 0x03]
        val digit = (a shr 4) and 0x03
        return "%c%d%X%02X".format(letter, digit, a and 0x0F, b)
    }

    /**
     * Fault codes from a mode 03 / 07 / 0A reply ([payload] starts with 0x43, 0x47 or 0x4A).
     * On CAN the second byte is the count; older protocols have no count byte.
     */
    fun dtcs(payload: IntArray): List<String> {
        if (payload.isEmpty()) return emptyList()
        var body = payload.drop(1)
        if (body.size % 2 == 1) body = body.drop(1) // CAN: the count byte
        return body.chunked(2).filter { it.size == 2 }.mapNotNull { dtc(it[0], it[1]) }.distinct()
    }

    /** ASCII text from mode 09 (VIN, calibration, ECU name), padding and the item count skipped. */
    fun ascii(payload: IntArray, skip: Int = 2): String {
        val body = payload.drop(skip)
        val text = body.filter { it in 0x20..0x7E }.map { it.toChar() }.joinToString("")
        return text.trim()
    }

    /** The VIN from a `49 02 …` reply: the last 17 printable characters. */
    fun vin(payload: IntArray): String? {
        val text = ascii(payload)
        return text.takeLast(17).takeIf { it.length == 17 }
    }
}
