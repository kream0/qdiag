package app.qdiag.core.elm

/**
 * What an ELM327 answered to one command, read up to its `>` prompt.
 *
 * The app talks with headers on (`ATH1`) and spaces on (`ATS1`): on the Qashqai's CAN bus
 * (ISO 15765-4, 11-bit, 500 kbit/s) every frame comes back as `7E8 06 41 0C 0B B8 00 00`, the
 * ECU's reply address, the ISO-TP byte, then the data. [ElmParser] also reads the header-less
 * form some clones fall back to.
 */
sealed interface ElmReply {
    val raw: List<String>

    /** OBD/UDS data, the payload of each answering ECU reassembled (multi-frame included). */
    data class Data(val byEcu: Map<String, IntArray>, override val raw: List<String>) : ElmReply {
        /** The first ECU's payload whose first bytes are [prefix] (e.g. 0x41, 0x0C for PID 0C). */
        fun payload(vararg prefix: Int): IntArray? = byEcu.values.firstOrNull { p ->
            p.size >= prefix.size && prefix.indices.all { p[it] == prefix[it] }
        }

        /** Every ECU's payload starting with [prefix], by ECU address. */
        fun payloads(vararg prefix: Int): Map<String, IntArray> = byEcu.filterValues { p ->
            p.size >= prefix.size && prefix.indices.all { p[it] == prefix[it] }
        }
    }

    /** A reply to an AT command: `OK`, `ELM327 v2.1`, `12.6V`, `A6`… */
    data class Text(val lines: List<String>, override val raw: List<String>) : ElmReply {
        val first: String get() = lines.firstOrNull().orEmpty()
    }

    data class Error(val kind: Kind, override val raw: List<String>) : ElmReply

    enum class Kind {
        /** The request was understood but no ECU answered (or it doesn't support that PID). */
        NO_DATA,
        /** `?`: the adapter didn't understand the command (clones lack some AT commands). */
        UNKNOWN_COMMAND,
        /** No protocol found: ignition off, or the adapter isn't plugged in properly. */
        UNABLE_TO_CONNECT,
        BUS_ERROR,
        CAN_ERROR,
        /** The adapter was interrupted (a command sent before the previous one finished). */
        STOPPED,
        BUFFER_FULL,
        /** No `>` prompt came back in time. */
        TIMEOUT,
        /** Something the parser couldn't read. */
        GARBLED,
    }
}
