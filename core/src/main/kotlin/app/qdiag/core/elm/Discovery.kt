package app.qdiag.core.elm

/**
 * What the car answers beyond the standard PIDs, so the app can be taught the Qashqai's own
 * data. Every probe only reads (OBD modes 01/06/09, KWP2000 service 21 and UDS service 22): no
 * session change, no write, no actuator test. Replies that carry the VIN are masked before
 * they leave the phone ([sensitive]).
 */
object Discovery {

    data class Probe(val command: String, val label: String, val timeoutMs: Long = 2_000, val sensitive: Boolean = false)

    /** Adapter and protocol details. */
    val adapter = listOf(
        Probe("ATI", "adapter version"),
        Probe("AT@1", "adapter description"),
        Probe("ATDP", "protocol"),
        Probe("ATDPN", "protocol number"),
        Probe("ATRV", "battery voltage"),
    )

    /** Standard OBD beyond live data: mode 09 vehicle info and mode 06 on-board test results. */
    val standard = listOf(
        Probe("0900", "mode 09 supported"),
        Probe("0902", "VIN", 4_000, sensitive = true),
        Probe("0904", "calibration ID", 4_000),
        Probe("0906", "calibration verification number", 4_000),
        Probe("090A", "ECU name", 4_000),
        Probe("0600", "mode 06 supported 01-20"),
        Probe("0620", "mode 06 supported 21-40"),
        Probe("0640", "mode 06 supported 41-60"),
        Probe("0660", "mode 06 supported 61-80"),
        Probe("0680", "mode 06 supported 81-A0"),
        Probe("06A0", "mode 06 supported A1-C0"),
    )

    /** Which ECUs answer OBD requests when addressed one by one (7E0–7E7). */
    val ecus = (0..7).flatMap { i ->
        listOf(Probe("ATSH7E$i", "address 7E$i"), Probe("0100", "7E$i supported PIDs", 1_500))
    } + listOf(Probe("ATSH7DF", "back to broadcast"))

    /**
     * The engine ECU's manufacturer data (Renault–Nissan, KWP2000 service 21 local identifiers
     * and UDS service 22 identifiers), addressed to 7E0 only.
     */
    val manufacturer: List<Probe> = buildList {
        add(Probe("ATSH7E0", "engine ECU"))
        add(Probe("ATCRA7E8", "listen to the engine only"))
        add(Probe("2180", "KWP ECU identification", 3_000))
        add(Probe("2181", "KWP VIN", 3_000, sensitive = true))
        for (lid in listOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x10, 0x11, 0x12, 0x13, 0x20, 0x21, 0x30, 0xA0, 0xA1)) {
            add(Probe("21%02X".format(lid), "KWP local id %02X".format(lid), 2_500))
        }
        for (did in listOf("F187", "F18C", "F190", "F191", "F194", "F195")) {
            add(Probe("22$did", "UDS $did", 2_500, sensitive = did == "F190"))
        }
        add(Probe("ATCRA", "listen to all"))
        add(Probe("ATSH7DF", "back to broadcast"))
    }

    val all: List<Probe> get() = adapter + standard + ecus + manufacturer

    /** Masks the serial part of anything that looks like a VIN (keeps the 11-character model part). */
    fun mask(text: String): String =
        Regex("[A-HJ-NPR-Z0-9]{17}").replace(text) { it.value.take(11) + "••••••" }
}
