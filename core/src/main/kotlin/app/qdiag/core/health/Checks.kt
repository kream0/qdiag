package app.qdiag.core.health

import app.qdiag.core.Text
import app.qdiag.core.obd.MonitorStatus

enum class Level { GOOD, INFO, WATCH, WARN }

/** One result of the health checks, with the parts it points to (keys of [app.qdiag.core.kb.Parts]). */
data class Finding(
    val id: String,
    val level: Level,
    val title: Text,
    val detail: Text,
    val parts: List<String> = emptyList(),
)

/** One reading of the live values (signal key → value) at [t] milliseconds. */
data class Sample(val t: Long, val values: Map<String, Double>) {
    operator fun get(key: String): Double? = values[key]
}

/**
 * Health checks over a drive's live readings, for the M9R 2.0 dCi. The figures are general
 * references for this kind of engine, not Nissan's specifications: a value far outside them is
 * worth a look, and the same check repeated over months (a trend) says more than one drive.
 */
class Analyzer {
    private val samples = ArrayList<Sample>()

    fun add(sample: Sample) {
        samples += sample
    }

    fun addAll(list: List<Sample>) {
        samples += list
    }

    val size: Int get() = samples.size

    private fun running(s: Sample) = (s["rpm"] ?: 0.0) > 450
    /** Engine known to be stopped: rpm read and zero (without rpm we can't tell). */
    private fun stopped(s: Sample) = s["rpm"]?.let { it < 100 } ?: false
    private fun median(xs: List<Double>): Double? = xs.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    private fun f1(x: Double) = "%.1f".format(x)
    private fun f0(x: Double) = "%.0f".format(x)

    fun findings(): List<Finding> = listOfNotNull(battery(), charging(), warmUp(), overheat(), boost(), rail(), maf(), egr())

    /** Resting voltage, engine off (the adapter's own reading, `ATRV`, or the ECU's). */
    private fun battery(): Finding? {
        // Above 13.3 V the alternator is still charging (or just stopped): not a resting voltage.
        val v = samples.filter { stopped(it) }.mapNotNull { it["battV"] ?: it["ecuV"] }.filter { it < 13.3 }
        val m = median(v) ?: return null
        val (level, en, fr) = when {
            m >= 12.4 -> Triple(Level.GOOD, "Battery charged", "Batterie chargée")
            m >= 12.0 -> Triple(Level.WATCH, "Battery partly discharged", "Batterie en partie déchargée")
            else -> Triple(Level.WARN, "Battery discharged", "Batterie déchargée")
        }
        return Finding(
            "battery", level, Text(en, fr),
            Text(
                "${f1(m)} V engine off (12.4 V or more is a charged battery; below 12.0 V it's flat or worn). Measure it after the car has rested a few hours.",
                "${f1(m)} V moteur arrêté (12,4 V ou plus : batterie chargée ; sous 12,0 V : vide ou usée). À mesurer après quelques heures de repos.",
            ),
            if (level == Level.GOOD) emptyList() else listOf("battery"),
        )
    }

    /** Charging voltage with the engine running. */
    private fun charging(): Finding? {
        val v = samples.filter { running(it) }.mapNotNull { it["battV"] ?: it["ecuV"] }
        if (v.size < 5) return null
        val m = median(v)!!
        val (level, en, fr) = when {
            m < 13.2 -> Triple(Level.WARN, "Alternator not charging enough", "L'alternateur charge trop peu")
            m > 15.0 -> Triple(Level.WARN, "Charging voltage too high", "Tension de charge trop haute")
            m < 13.5 -> Triple(Level.WATCH, "Charging voltage a little low", "Tension de charge un peu basse")
            else -> Triple(Level.GOOD, "Charging OK", "Charge correcte")
        }
        return Finding(
            "charging", level, Text(en, fr),
            Text("${f1(m)} V engine running (13.5 to 14.8 V is normal).", "${f1(m)} V moteur tournant (13,5 à 14,8 V est normal)."),
            if (level == Level.GOOD) emptyList() else listOf("alternator", "battery"),
        )
    }

    /** How long the coolant takes to warm up from a cold start: a slow warm-up points to the thermostat. */
    private fun warmUp(): Finding? {
        val run = samples.filter { running(it) && it["coolant"] != null }
        if (run.size < 10) return null
        val start = run.first()
        if (start["coolant"]!! > 40) return null
        val warm = run.firstOrNull { it["coolant"]!! >= 75 }
        val drivenMin = (run.last().t - start.t) / 60_000.0
        return if (warm != null) {
            val min = (warm.t - start.t) / 60_000.0
            val level = if (min <= 15) Level.GOOD else Level.WATCH
            Finding(
                "warmup", level,
                Text(if (level == Level.GOOD) "Normal warm-up" else "Slow warm-up", if (level == Level.GOOD) "Montée en température normale" else "Montée en température lente"),
                Text(
                    "From ${f0(start["coolant"]!!)} °C to 75 °C in ${f0(min)} min. A diesel takes longer in winter or at idle; well over 15 min while driving can mean the thermostat stays open.",
                    "De ${f0(start["coolant"]!!)} °C à 75 °C en ${f0(min)} min. Un diesel est plus lent l'hiver ou au ralenti ; nettement plus de 15 min en roulant peut venir d'un thermostat resté ouvert.",
                ),
                if (level == Level.GOOD) emptyList() else listOf("thermostat"),
            )
        } else if (drivenMin >= 20) {
            val max = run.maxOf { it["coolant"]!! }
            Finding(
                "warmup", Level.WARN, Text("Engine never warmed up", "Le moteur ne monte pas en température"),
                Text(
                    "Still ${f0(max)} °C after ${f0(drivenMin)} min running: the thermostat is probably stuck open (or the coolant sensor reads wrong).",
                    "Toujours ${f0(max)} °C après ${f0(drivenMin)} min de fonctionnement : le thermostat reste probablement ouvert (ou la sonde de température lit faux).",
                ),
                listOf("thermostat", "coolantSensor"),
            )
        } else null
    }

    private fun overheat(): Finding? {
        val max = samples.mapNotNull { it["coolant"] }.maxOrNull() ?: return null
        if (max < 100) return null
        val level = if (max >= 106) Level.WARN else Level.WATCH
        return Finding(
            "overheat", level, Text("Coolant running hot", "Liquide de refroidissement très chaud"),
            Text(
                "Up to ${f0(max)} °C (it normally stays around 85–95 °C). Check the coolant level when cold, the fan and the water pump.",
                "Jusqu'à ${f0(max)} °C (normalement autour de 85 à 95 °C). Vérifier le niveau à froid, le ventilateur et la pompe à eau.",
            ),
            listOf("thermostat", "waterPump", "coolantSensor"),
        )
    }

    /** Full-load samples: pedal or load near the top, in the turbo's working range. */
    private fun fullLoad(): List<Sample> = samples.filter {
        val rpm = it["rpm"] ?: return@filter false
        val heavy = (it["pedal"] ?: 0.0) >= 80 || (it["load"] ?: 0.0) >= 85
        heavy && rpm in 1800.0..3800.0
    }

    /** Turbo: boost reached at full load, and how close it gets to what the ECU asks for. */
    private fun boost(): Finding? {
        val full = fullLoad()
        val baro = median(samples.mapNotNull { it["baro"] }) ?: 100.0
        val pressures = full.mapNotNull { it["boost"] ?: it["mapA"] ?: it["map"] }
        if (pressures.size < 3) {
            if (samples.size < 30) return null
            return Finding(
                "boost", Level.INFO, Text("Turbo not tested yet", "Turbo pas encore testé"),
                Text(
                    "To test it: in 3rd gear, from 1,500 rpm, floor the pedal up to about 3,500 rpm (on a safe, clear road).",
                    "Pour le tester : en 3e, à partir de 1 500 tr/min, accélérer à fond jusqu'à environ 3 500 tr/min (sur une route dégagée et sûre).",
                ),
            )
        }
        val peak = pressures.sorted().let { it[(it.size * 9) / 10] } - baro
        val gaps = full.mapNotNull { s -> s["boostCmd"]?.let { cmd -> (s["boost"] ?: s["mapA"] ?: s["map"])?.let { cmd - it } } }
        val gap = median(gaps)
        val level = when {
            peak < 60 -> Level.WARN
            gap != null && gap > 25 -> Level.WARN
            peak < 90 -> Level.WATCH
            else -> Level.GOOD
        }
        val gapText = gap?.let { Text(" The ECU asked for ${f0(it)} kPa more than it got.", " Le calculateur demandait ${f0(it)} kPa de plus que ce qui a été atteint.") }
        return Finding(
            "boost", level,
            Text(if (level == Level.GOOD) "Turbo boost OK" else "Turbo boost low", if (level == Level.GOOD) "Suralimentation correcte" else "Suralimentation faible"),
            Text(
                "About ${f0(peak)} kPa of boost at full load (a healthy 2.0 dCi 150 makes roughly 100–150 kPa).${gapText?.en.orEmpty()}",
                "Environ ${f0(peak)} kPa de suralimentation à pleine charge (un 2.0 dCi 150 en bon état fait à peu près 100 à 150 kPa).${gapText?.fr.orEmpty()}",
            ),
            if (level == Level.GOOD) emptyList() else listOf("turboSolenoid", "boostSensor"),
        )
    }

    /** Common-rail pressure at warm idle, and against the commanded pressure. */
    private fun rail(): Finding? {
        val idle = samples.filter { (it["rpm"] ?: 0.0) in 600.0..1000.0 && (it["speed"] ?: 0.0) < 2 && (it["coolant"] ?: 0.0) > 60 }
        val p = median(idle.mapNotNull { it["rail"] ?: it["railAct"] }) ?: return null
        val gaps = samples.mapNotNull { s -> s["railCmd"]?.let { c -> (s["railAct"] ?: s["rail"])?.let { a -> c - a } } }
        val gap = median(gaps.map { kotlin.math.abs(it) })
        val level = when {
            p < 150 -> Level.WATCH
            gap != null && gap > 100 -> Level.WATCH
            else -> Level.GOOD
        }
        return Finding(
            "rail", level,
            Text(if (level == Level.GOOD) "Fuel rail pressure OK at idle" else "Fuel rail pressure to check", if (level == Level.GOOD) "Pression de rampe correcte au ralenti" else "Pression de rampe à vérifier"),
            Text(
                "${f0(p)} bar at warm idle.${gap?.let { " Typical gap to the commanded pressure: ${f0(it)} bar." }.orEmpty()} Low pressure often starts with a clogged fuel filter.",
                "${f0(p)} bar au ralenti moteur chaud.${gap?.let { " Écart habituel avec la pression commandée : ${f0(it)} bar." }.orEmpty()} Une pression faible commence souvent par un filtre à gazole colmaté.",
            ),
            if (level == Level.GOOD) emptyList() else listOf("fuelFilter", "railRegulator", "railSensor"),
        )
    }

    /** Air flow at full load and high revs: low readings point to the air flow sensor, the air filter or the turbo. */
    private fun maf(): Finding? {
        val high = fullLoad().filter { (it["rpm"] ?: 0.0) >= 3000 }.mapNotNull { it["maf"] }
        if (high.size < 2) return null
        val peak = high.maxOrNull()!!
        val level = if (peak < 80) Level.WATCH else Level.GOOD
        return Finding(
            "maf", level,
            Text(if (level == Level.GOOD) "Air flow OK at full load" else "Air flow low at full load", if (level == Level.GOOD) "Débit d'air correct à pleine charge" else "Débit d'air faible à pleine charge"),
            Text(
                "${f0(peak)} g/s above 3,000 rpm at full load (roughly 100–140 g/s is expected for this engine).",
                "${f0(peak)} g/s au-dessus de 3 000 tr/min à pleine charge (environ 100 à 140 g/s attendus pour ce moteur).",
            ),
            if (level == Level.GOOD) emptyList() else listOf("maf", "airFilter", "turboSolenoid"),
        )
    }

    /** EGR: a valve that doesn't follow its command (sooted up or sticking). */
    private fun egr(): Finding? {
        val errs = samples.mapNotNull { s ->
            val cmd = s["egrCmd"] ?: s["egrACmd"] ?: return@mapNotNull null
            if (cmd < 10) return@mapNotNull null
            s["egrErr"] ?: s["egrAErr"]
        }
        if (errs.size < 10) return null
        val m = median(errs.map { kotlin.math.abs(it) })!!
        val level = if (m > 20) Level.WATCH else Level.GOOD
        return Finding(
            "egr", level,
            Text(if (level == Level.GOOD) "EGR valve follows its command" else "EGR valve lags its command", if (level == Level.GOOD) "La vanne EGR suit sa commande" else "La vanne EGR suit mal sa commande"),
            Text("Typical gap: ${f0(m)} % (over 20 % suggests soot or a sticking valve).", "Écart habituel : ${f0(m)} % (au-delà de 20 % : vanne encrassée ou qui accroche)."),
            if (level == Level.GOOD) emptyList() else listOf("egrValve"),
        )
    }
}

/**
 * Ready for the contrôle technique? The inspector reads the check-engine light and, on recent
 * cars, the OBD: no stored code, the light off, and the readiness monitors complete (codes
 * cleared just before the test leave monitors incomplete, which shows).
 */
object Readiness {
    fun check(status: MonitorStatus?, stored: List<String>, pending: List<String>, distanceSinceClearKm: Double?): List<Finding> {
        val out = ArrayList<Finding>()
        if (status != null) {
            out += if (status.milOn) Finding(
                "mil", Level.WARN, Text("Check-engine light on", "Voyant moteur allumé"),
                Text("The light is on: at the contrôle technique this usually means a retest.", "Le voyant est allumé : au contrôle technique, c'est en général une contre-visite."),
            ) else Finding(
                "mil", Level.GOOD, Text("Check-engine light off", "Voyant moteur éteint"),
                Text("The ECU doesn't ask for the light.", "Le calculateur ne demande pas d'allumer le voyant."),
            )
        }
        out += if (stored.isNotEmpty()) Finding(
            "stored", Level.WARN, Text("Stored fault codes", "Codes défaut enregistrés"),
            Text("${stored.joinToString(", ")}: see the Codes tab.", "${stored.joinToString(", ")} : voir l'onglet Codes."),
        ) else Finding("stored", Level.GOOD, Text("No stored fault code", "Aucun code défaut enregistré"), Text("Nothing stored in the engine ECU.", "Rien d'enregistré dans le calculateur moteur."))
        if (pending.isNotEmpty()) out += Finding(
            "pending", Level.WATCH, Text("Pending codes", "Codes en attente"),
            Text("${pending.joinToString(", ")}: seen once, not confirmed yet.", "${pending.joinToString(", ")} : vus une fois, pas encore confirmés."),
        )
        if (status != null) {
            val incomplete = status.incomplete
            out += if (incomplete.isEmpty()) Finding(
                "monitors", Level.GOOD, Text("Readiness monitors complete", "Tests de bon fonctionnement terminés"),
                Text("Every self-test the ECU runs has completed.", "Tous les autotests du calculateur sont terminés."),
            ) else Finding(
                "monitors", Level.WATCH, Text("Readiness monitors not finished", "Autotests pas encore terminés"),
                Text(
                    "Not finished: ${incomplete.joinToString(", ") { it.name.en }}. A mixed drive (town, road, motorway, ~50 km) usually completes them.",
                    "Pas terminés : ${incomplete.joinToString(", ") { it.name.fr }}. Un trajet mixte (ville, route, autoroute, ~50 km) les termine en général.",
                ),
            )
        }
        if (distanceSinceClearKm != null && distanceSinceClearKm < 100) out += Finding(
            "cleared", Level.INFO, Text("Codes cleared recently", "Codes effacés récemment"),
            Text("Only ${"%.0f".format(distanceSinceClearKm)} km since the codes were cleared.", "Seulement ${"%.0f".format(distanceSinceClearKm)} km depuis l'effacement des codes."),
        )
        return out
    }
}
