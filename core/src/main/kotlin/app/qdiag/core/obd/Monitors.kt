package app.qdiag.core.obd

import app.qdiag.core.Text

/** One readiness monitor: whether the ECU runs it and whether it has completed since codes were cleared. */
data class Monitor(val name: Text, val supported: Boolean, val complete: Boolean)

/**
 * PID 01, "monitor status since DTCs cleared": the check-engine light (MIL), how many codes are
 * stored, and the readiness monitors. The Qashqai's M9R is a compression-ignition engine, so the
 * non-continuous monitors read with the diesel table (catalyst, NOx, boost, exhaust sensor, DPF, EGR).
 */
data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val diesel: Boolean,
    val monitors: List<Monitor>,
) {
    val incomplete: List<Monitor> get() = monitors.filter { it.supported && !it.complete }

    companion object {
        private val CONTINUOUS = listOf(
            Text("Misfire", "Ratés de combustion"),
            Text("Fuel system", "Système d'alimentation"),
            Text("Components", "Composants"),
        )
        private val DIESEL = listOf(
            Text("NMHC catalyst", "Catalyseur (HC)"),
            Text("NOx / SCR", "NOx / SCR"),
            null,
            Text("Boost pressure", "Pression de suralimentation"),
            null,
            Text("Exhaust gas sensor", "Sonde des gaz d'échappement"),
            Text("Particulate filter (DPF)", "Filtre à particules (FAP)"),
            Text("EGR / VVT", "Vanne EGR / distribution variable"),
        )
        private val PETROL = listOf(
            Text("Catalyst", "Catalyseur"),
            Text("Heated catalyst", "Catalyseur chauffé"),
            Text("Evaporative system", "Système d'évaporation"),
            Text("Secondary air", "Air secondaire"),
            null,
            Text("Oxygen sensor", "Sonde lambda"),
            Text("Oxygen sensor heater", "Chauffage de sonde lambda"),
            Text("EGR / VVT", "Vanne EGR / distribution variable"),
        )

        /** From a `41 01 A B C D` reply. */
        fun parse(payload: IntArray): MonitorStatus? {
            if (payload.size < 6 || payload[0] != 0x41 || payload[1] != 0x01) return null
            val (a, b, c, d) = listOf(payload[2], payload[3], payload[4], payload[5])
            val diesel = b and 0x08 != 0
            val monitors = ArrayList<Monitor>()
            for (i in 0 until 3) {
                monitors += Monitor(CONTINUOUS[i], supported = b and (1 shl i) != 0, complete = b and (1 shl (i + 4)) == 0)
            }
            val table = if (diesel) DIESEL else PETROL
            for (i in 0 until 8) {
                val name = table[i] ?: continue
                monitors += Monitor(name, supported = c and (1 shl i) != 0, complete = d and (1 shl i) == 0)
            }
            return MonitorStatus(milOn = a and 0x80 != 0, dtcCount = a and 0x7F, diesel = diesel, monitors = monitors)
        }
    }
}
