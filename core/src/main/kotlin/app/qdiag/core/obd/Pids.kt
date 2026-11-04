package app.qdiag.core.obd

import app.qdiag.core.Text

/** One decoded value (a PID can carry several, e.g. commanded and actual boost). */
data class Signal(
    val key: String,
    val name: Text,
    val unit: String,
    val value: Double,
    val decimals: Int = 0,
) {
    fun formatted(): String = if (decimals == 0) "%d".format(Math.round(value)) else "%.${decimals}f".format(value)
}

enum class PidGroup(val title: Text) {
    ENGINE(Text("Engine", "Moteur")),
    AIR(Text("Air & turbo", "Air et turbo")),
    FUEL(Text("Fuel & injection", "Carburant et injection")),
    EXHAUST(Text("Exhaust, EGR & DPF", "Échappement, EGR et FAP")),
    STATUS(Text("Counters & status", "Compteurs et état")),
}

/** A mode 01 PID the app knows how to read, with SAE J1979's scaling. */
class PidDef(
    val pid: Int,
    val name: Text,
    val group: PidGroup,
    /** Reads the data bytes (after `41 <pid>`); empty when the reply is too short. */
    val decode: (IntArray) -> List<Signal>,
) {
    val hex: String get() = "%02X".format(pid)
}

object Pids {

    private fun u16(d: IntArray, i: Int) = d[i] * 256 + d[i + 1]
    private fun s16(d: IntArray, i: Int) = u16(d, i).let { if (it >= 0x8000) it - 0x10000 else it }

    private fun one(pid: Int, key: String, en: String, fr: String, unit: String, group: PidGroup, bytes: Int, decimals: Int = 0, f: (IntArray) -> Double) =
        PidDef(pid, Text(en, fr), group) { d -> if (d.size < bytes) emptyList() else listOf(Signal(key, Text(en, fr), unit, f(d), decimals)) }

    private fun pct(a: Int) = a * 100.0 / 255

    val all: List<PidDef> = listOf(
        one(0x04, "load", "Calculated load", "Charge calculée", "%", PidGroup.ENGINE, 1) { pct(it[0]) },
        one(0x05, "coolant", "Coolant temperature", "Température du liquide de refroidissement", "°C", PidGroup.ENGINE, 1) { it[0] - 40.0 },
        one(0x0B, "map", "Intake manifold pressure (absolute)", "Pression d'admission (absolue)", "kPa", PidGroup.AIR, 1) { it[0].toDouble() },
        one(0x0C, "rpm", "Engine speed", "Régime moteur", "tr/min", PidGroup.ENGINE, 2) { u16(it, 0) / 4.0 },
        one(0x0D, "speed", "Vehicle speed", "Vitesse", "km/h", PidGroup.ENGINE, 1) { it[0].toDouble() },
        one(0x0F, "iat", "Intake air temperature", "Température d'air d'admission", "°C", PidGroup.AIR, 1) { it[0] - 40.0 },
        one(0x10, "maf", "Mass air flow", "Débit d'air (débitmètre)", "g/s", PidGroup.AIR, 2, 1) { u16(it, 0) / 100.0 },
        one(0x11, "throttle", "Throttle position", "Position du papillon", "%", PidGroup.AIR, 1) { pct(it[0]) },
        one(0x1F, "runTime", "Run time since start", "Temps depuis le démarrage", "s", PidGroup.STATUS, 2) { u16(it, 0).toDouble() },
        one(0x21, "distMil", "Distance with check-engine light on", "Distance avec voyant moteur allumé", "km", PidGroup.STATUS, 2) { u16(it, 0).toDouble() },
        one(0x22, "railRel", "Fuel rail pressure (relative)", "Pression de rampe (relative)", "kPa", PidGroup.FUEL, 2) { u16(it, 0) * 0.079 },
        one(0x23, "rail", "Fuel rail pressure", "Pression de rampe commune", "bar", PidGroup.FUEL, 2) { u16(it, 0) * 0.1 },
        one(0x2C, "egrCmd", "Commanded EGR", "EGR commandée", "%", PidGroup.EXHAUST, 1) { pct(it[0]) },
        one(0x2D, "egrErr", "EGR error", "Écart EGR", "%", PidGroup.EXHAUST, 1, 1) { it[0] * 100.0 / 128 - 100 },
        one(0x2F, "fuelLevel", "Fuel level", "Niveau de carburant", "%", PidGroup.FUEL, 1) { pct(it[0]) },
        one(0x30, "warmups", "Warm-ups since codes cleared", "Montées en température depuis l'effacement", "", PidGroup.STATUS, 1) { it[0].toDouble() },
        one(0x31, "distClear", "Distance since codes cleared", "Distance depuis l'effacement des codes", "km", PidGroup.STATUS, 2) { u16(it, 0).toDouble() },
        one(0x33, "baro", "Barometric pressure", "Pression atmosphérique", "kPa", PidGroup.AIR, 1) { it[0].toDouble() },
        one(0x42, "ecuV", "Control module voltage", "Tension du calculateur", "V", PidGroup.ENGINE, 2, 2) { u16(it, 0) / 1000.0 },
        one(0x43, "loadAbs", "Absolute load", "Charge absolue", "%", PidGroup.ENGINE, 2) { u16(it, 0) * 100.0 / 255 },
        one(0x46, "ambient", "Ambient air temperature", "Température extérieure", "°C", PidGroup.ENGINE, 1) { it[0] - 40.0 },
        one(0x49, "pedal", "Accelerator pedal position", "Position de la pédale d'accélérateur", "%", PidGroup.ENGINE, 1) { pct(it[0]) },
        one(0x4C, "throttleCmd", "Commanded throttle", "Papillon commandé", "%", PidGroup.AIR, 1) { pct(it[0]) },
        one(0x4D, "timeMil", "Time with check-engine light on", "Temps avec voyant moteur allumé", "min", PidGroup.STATUS, 2) { u16(it, 0).toDouble() },
        one(0x4E, "timeClear", "Time since codes cleared", "Temps depuis l'effacement des codes", "min", PidGroup.STATUS, 2) { u16(it, 0).toDouble() },
        one(0x5C, "oil", "Engine oil temperature", "Température d'huile", "°C", PidGroup.ENGINE, 1) { it[0] - 40.0 },
        one(0x5D, "injTiming", "Injection timing", "Avance à l'injection", "°", PidGroup.FUEL, 2, 1) { u16(it, 0) / 128.0 - 210 },
        one(0x5E, "fuelRate", "Fuel rate", "Débit de carburant", "L/h", PidGroup.FUEL, 2, 1) { u16(it, 0) / 20.0 },
        one(0x61, "torqueDem", "Driver's demanded torque", "Couple demandé", "%", PidGroup.ENGINE, 1) { it[0] - 125.0 },
        one(0x62, "torqueAct", "Actual engine torque", "Couple réel", "%", PidGroup.ENGINE, 1) { it[0] - 125.0 },
        one(0x63, "torqueRef", "Reference torque", "Couple de référence", "N·m", PidGroup.ENGINE, 2) { u16(it, 0).toDouble() },
        PidDef(0x69, Text("EGR position", "Position de la vanne EGR"), PidGroup.EXHAUST) { d ->
            if (d.size < 4) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("egrACmd", Text("EGR A commanded", "EGR A commandée"), "%", pct(d[1])))
                if (d[0] and 0x02 != 0) add(Signal("egrA", Text("EGR A actual", "EGR A réelle"), "%", pct(d[2])))
                if (d[0] and 0x04 != 0) add(Signal("egrAErr", Text("EGR A error", "Écart EGR A"), "%", d[3] * 100.0 / 128 - 100, 1))
            }
        },
        PidDef(0x6A, Text("Intake air flow control", "Doseur d'air (volet d'admission)"), PidGroup.AIR) { d ->
            if (d.size < 3) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("intakeCmd", Text("Intake flap commanded", "Volet d'admission commandé"), "%", pct(d[1])))
                if (d[0] and 0x02 != 0) add(Signal("intakePos", Text("Intake flap position", "Position du volet d'admission"), "%", pct(d[2])))
            }
        },
        PidDef(0x6D, Text("Fuel pressure control", "Régulation de pression carburant"), PidGroup.FUEL) { d ->
            if (d.size < 5) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("railCmd", Text("Commanded rail pressure", "Pression de rampe commandée"), "bar", u16(d, 1) * 0.1))
                if (d[0] and 0x02 != 0) add(Signal("railAct", Text("Rail pressure (control)", "Pression de rampe (régulation)"), "bar", u16(d, 3) * 0.1))
            }
        },
        PidDef(0x70, Text("Boost pressure control", "Régulation de la suralimentation"), PidGroup.AIR) { d ->
            if (d.size < 5) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("boostCmd", Text("Commanded boost (absolute)", "Suralimentation commandée (absolue)"), "kPa", u16(d, 1) * 0.03125))
                if (d[0] and 0x02 != 0) add(Signal("boost", Text("Actual boost (absolute)", "Suralimentation réelle (absolue)"), "kPa", u16(d, 3) * 0.03125))
            }
        },
        PidDef(0x71, Text("Turbo vanes (VGT)", "Ailettes du turbo (géométrie variable)"), PidGroup.AIR) { d ->
            if (d.size < 3) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("vgtCmd", Text("VGT commanded", "Turbo commandé"), "%", pct(d[1])))
                if (d[0] and 0x02 != 0) add(Signal("vgt", Text("VGT position", "Position du turbo"), "%", pct(d[2])))
            }
        },
        PidDef(0x73, Text("Exhaust pressure", "Pression d'échappement"), PidGroup.EXHAUST) { d ->
            if (d.size < 3 || d[0] and 0x01 == 0) emptyList()
            else listOf(Signal("exhP", Text("Exhaust pressure", "Pression d'échappement"), "kPa", u16(d, 1) * 0.01, 1))
        },
        PidDef(0x78, Text("Exhaust gas temperatures", "Températures des gaz d'échappement"), PidGroup.EXHAUST) { d ->
            if (d.size < 3) emptyList() else buildList {
                for (i in 0 until 4) {
                    if (d[0] and (1 shl i) != 0 && d.size >= 3 + i * 2) {
                        add(Signal("egt${i + 1}", Text("Exhaust temperature ${i + 1}", "Température d'échappement ${i + 1}"), "°C", u16(d, 1 + i * 2) * 0.1 - 40))
                    }
                }
            }
        },
        PidDef(0x7A, Text("Particulate filter pressures", "Pressions du filtre à particules"), PidGroup.EXHAUST) { d ->
            if (d.size < 3) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("dpfDp", Text("DPF differential pressure", "Pression différentielle du FAP"), "kPa", s16(d, 1) * 0.01, 2))
                if (d[0] and 0x02 != 0 && d.size >= 5) add(Signal("dpfIn", Text("DPF inlet pressure", "Pression en entrée de FAP"), "kPa", u16(d, 3) * 0.01, 2))
                if (d[0] and 0x04 != 0 && d.size >= 7) add(Signal("dpfOut", Text("DPF outlet pressure", "Pression en sortie de FAP"), "kPa", u16(d, 5) * 0.01, 2))
            }
        },
        PidDef(0x7C, Text("Particulate filter temperatures", "Températures du filtre à particules"), PidGroup.EXHAUST) { d ->
            if (d.size < 3) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("dpfTin", Text("DPF inlet temperature", "Température en entrée de FAP"), "°C", u16(d, 1) * 0.1 - 40))
                if (d[0] and 0x02 != 0 && d.size >= 5) add(Signal("dpfTout", Text("DPF outlet temperature", "Température en sortie de FAP"), "°C", u16(d, 3) * 0.1 - 40))
            }
        },
        PidDef(0x87, Text("Intake manifold pressure (sensors)", "Pression d'admission (capteurs)"), PidGroup.AIR) { d ->
            if (d.size < 3) emptyList() else buildList {
                if (d[0] and 0x01 != 0) add(Signal("mapA", Text("Intake pressure A", "Pression d'admission A"), "kPa", u16(d, 1) / 32.0))
            }
        },
    )

    private val byPid = all.associateBy { it.pid }

    operator fun get(pid: Int): PidDef? = byPid[pid]

    /** Decodes a `41 <pid> …` payload with the app's table (null for an unknown PID). */
    fun decode(payload: IntArray): List<Signal>? {
        if (payload.size < 2 || payload[0] != 0x41) return null
        val def = byPid[payload[1]] ?: return null
        return def.decode(payload.copyOfRange(2, payload.size))
    }
}
