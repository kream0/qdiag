package app.qdiag.core.kb

import app.qdiag.core.Text

enum class Severity(val label: Text) {
    INFO(Text("Information", "Information")),
    WATCH(Text("Keep an eye on it", "À surveiller")),
    FIX(Text("Get it fixed soon", "À faire réparer rapidement")),
    STOP(Text("Stop the engine", "Arrêter le moteur")),
}

/** What the app says about a fault code. */
data class DtcInfo(
    val code: String,
    val title: Text,
    val system: Text,
    val severity: Severity,
    /** Likely causes on this engine (the M9R 2.0 dCi), most likely first; null when generic. */
    val advice: Text? = null,
    val parts: List<String> = emptyList(),
    /** False when only the code's family is known (a manufacturer code, or one not in the table). */
    val known: Boolean = true,
)

/**
 * Fault codes for the Qashqai+2's 2.0 dCi (M9R): the SAE / ISO 15031-6 meaning of each generic
 * code the engine can set, and for the common ones what usually causes it on this engine and which
 * parts it points to (see [Parts]).
 */
object Dtcs {

    private class E(val title: Text, val severity: Severity, val advice: Text? = null, val parts: List<String> = emptyList())

    private fun t(en: String, fr: String) = Text(en, fr)

    private val FUEL_LOW = t(
        "On the M9R, start with the fuel filter (a clogged one is the usual cause), then air in the fuel lines, the pressure regulator, the high-pressure pump, and injectors leaking too much back.",
        "Sur le M9R, commencer par le filtre à gazole (un filtre colmaté est la cause habituelle), puis une prise d'air sur le circuit, le régulateur de pression, la pompe haute pression et des injecteurs qui fuient trop au retour.",
    )
    private val EGR = t(
        "EGR valves clog with soot on this engine: cleaning it often fixes the code, otherwise replace it. Check its connector and wiring too.",
        "La vanne EGR s'encrasse beaucoup sur ce moteur : un nettoyage suffit souvent, sinon la remplacer. Vérifier aussi sa prise et son câblage.",
    )
    private val UNDERBOOST = t(
        "Look for a split or loose intercooler hose first (very common), then the turbo control solenoid and its vacuum hoses, sticking turbo vanes, and the boost sensor.",
        "Chercher d'abord une durite d'air (intercooler) fendue ou déboîtée (très fréquent), puis l'électrovanne de commande du turbo et ses durites de dépression, des ailettes de turbo grippées, et le capteur de pression.",
    )
    private val DPF = t(
        "Drive 20–30 minutes on the motorway at 2,500–3,000 rpm to let the filter regenerate. If the code comes back, have a forced regeneration done and check the differential pressure sensor and its two small hoses.",
        "Rouler 20 à 30 minutes sur autoroute entre 2 500 et 3 000 tr/min pour laisser le filtre se régénérer. Si le code revient : régénération forcée au garage, et vérifier le capteur de pression différentielle et ses deux petites durites.",
    )
    private val GLOW = t(
        "Worn glow plugs (hard cold starts, white smoke) or the glow plug control unit. Plugs are cheap: replace all four together.",
        "Bougies de préchauffage usées (démarrages difficiles à froid, fumée blanche) ou boîtier de préchauffage. Les bougies coûtent peu : changer les quatre ensemble.",
    )
    private val SWIRL = t(
        "The intake manifold's swirl flaps or their actuator stick with soot: a known M9R weakness. Cleaning the manifold usually cures it.",
        "Les volets de turbulence du collecteur d'admission ou leur moteur se grippent avec la suie : point faible connu du M9R. Un nettoyage du collecteur suffit en général.",
    )
    private val SENSOR_WIRING = t(
        "Usually the sensor itself or its connector (corrosion, a loose pin), sometimes a chafed wire.",
        "En général le capteur lui-même ou sa prise (oxydation, broche desserrée), parfois un fil abîmé.",
    )
    private val VOLTAGE = t(
        "Check the battery (12.4 V or more engine off) and the charging voltage (13.6–14.7 V running), then the alternator and the battery terminals.",
        "Vérifier la batterie (12,4 V ou plus moteur arrêté) et la tension de charge (13,6 à 14,7 V moteur tournant), puis l'alternateur et les cosses de batterie.",
    )

    private val table: Map<String, E> = buildMap {
        fun put(code: String, en: String, fr: String, sev: Severity, advice: Text? = null, vararg parts: String) =
            put(code, E(t(en, fr), sev, advice, parts.toList()))

        put("P0016", "Crankshaft / camshaft position correlation", "Corrélation régime vilebrequin / arbre à cames", Severity.FIX,
            t("Check both position sensors and their wiring first; a stretched timing chain gives the same code.", "Vérifier d'abord les deux capteurs et leur câblage ; une chaîne de distribution détendue donne le même code."),
            "camSensor", "crankSensor")
        put("P0045", "Turbo boost control solenoid circuit", "Circuit de l'électrovanne de commande du turbo", Severity.FIX, SENSOR_WIRING, "turboSolenoid")
        put("P0046", "Turbo boost control solenoid circuit range", "Électrovanne du turbo : plage incorrecte", Severity.FIX, SENSOR_WIRING, "turboSolenoid")
        put("P0047", "Turbo boost control solenoid circuit low", "Électrovanne du turbo : signal bas", Severity.FIX, SENSOR_WIRING, "turboSolenoid")
        put("P0048", "Turbo boost control solenoid circuit high", "Électrovanne du turbo : signal haut", Severity.FIX, SENSOR_WIRING, "turboSolenoid")
        put("P0087", "Fuel rail pressure too low", "Pression de rampe trop basse", Severity.FIX, FUEL_LOW, "fuelFilter", "railRegulator", "railSensor", "injPump")
        put("P0088", "Fuel rail pressure too high", "Pression de rampe trop haute", Severity.FIX,
            t("Usually the pressure regulator or the rail pressure sensor.", "En général le régulateur de pression ou le capteur de pression de rampe."), "railRegulator", "railSensor")
        put("P0089", "Fuel pressure regulator performance", "Régulateur de pression : fonctionnement", Severity.FIX, FUEL_LOW, "railRegulator", "fuelFilter")
        put("P0090", "Fuel pressure regulator control circuit", "Circuit de commande du régulateur de pression", Severity.FIX, SENSOR_WIRING, "railRegulator")
        put("P0091", "Fuel pressure regulator control circuit low", "Régulateur de pression : signal bas", Severity.FIX, SENSOR_WIRING, "railRegulator")
        put("P0092", "Fuel pressure regulator control circuit high", "Régulateur de pression : signal haut", Severity.FIX, SENSOR_WIRING, "railRegulator")
        put("P0093", "Fuel system large leak detected", "Fuite importante sur le circuit de carburant", Severity.STOP,
            t("Stop and look for diesel leaking around the pump, rail, pipes and injectors (fire risk).", "S'arrêter et chercher une fuite de gazole autour de la pompe, de la rampe, des tuyaux et des injecteurs (risque d'incendie)."),
            "rail", "injector", "injPump")
        put("P0100", "Mass air flow circuit", "Circuit du débitmètre d'air", Severity.FIX, SENSOR_WIRING, "maf")
        put("P0101", "Mass air flow range / performance", "Débitmètre d'air : plage ou fonctionnement", Severity.FIX,
            t("A dirty or ageing air flow sensor, an air leak after it, a clogged air filter, or an EGR valve stuck open.", "Débitmètre encrassé ou usé, prise d'air après lui, filtre à air colmaté, ou vanne EGR restée ouverte."),
            "maf", "airFilter", "egrValve")
        put("P0102", "Mass air flow circuit low", "Débitmètre d'air : signal bas", Severity.FIX, SENSOR_WIRING, "maf")
        put("P0103", "Mass air flow circuit high", "Débitmètre d'air : signal haut", Severity.FIX, SENSOR_WIRING, "maf")
        put("P0105", "Manifold pressure / barometric circuit", "Circuit du capteur de pression d'admission", Severity.FIX, SENSOR_WIRING, "boostSensor")
        put("P0106", "Manifold pressure range / performance", "Capteur de pression d'admission : plage ou fonctionnement", Severity.FIX, SENSOR_WIRING, "boostSensor")
        put("P0107", "Manifold pressure circuit low", "Capteur de pression d'admission : signal bas", Severity.FIX, SENSOR_WIRING, "boostSensor")
        put("P0108", "Manifold pressure circuit high", "Capteur de pression d'admission : signal haut", Severity.FIX, SENSOR_WIRING, "boostSensor")
        put("P0110", "Intake air temperature circuit", "Circuit de température d'air d'admission", Severity.WATCH, SENSOR_WIRING, "maf")
        put("P0112", "Intake air temperature circuit low", "Température d'air d'admission : signal bas", Severity.WATCH, SENSOR_WIRING, "maf")
        put("P0113", "Intake air temperature circuit high", "Température d'air d'admission : signal haut", Severity.WATCH, SENSOR_WIRING, "maf")
        put("P0115", "Coolant temperature circuit", "Circuit de température du liquide de refroidissement", Severity.FIX, SENSOR_WIRING, "coolantSensor")
        put("P0116", "Coolant temperature range / performance", "Température du liquide : plage ou fonctionnement", Severity.FIX,
            t("The coolant temperature sensor, or a thermostat stuck open.", "La sonde de température, ou un thermostat resté ouvert."), "coolantSensor", "thermostat")
        put("P0117", "Coolant temperature circuit low", "Température du liquide : signal bas", Severity.FIX, SENSOR_WIRING, "coolantSensor")
        put("P0118", "Coolant temperature circuit high", "Température du liquide : signal haut", Severity.FIX, SENSOR_WIRING, "coolantSensor")
        put("P0128", "Coolant below thermostat temperature", "Liquide de refroidissement sous la température du thermostat", Severity.WATCH,
            t("The thermostat is stuck open: the engine warms up slowly, uses more fuel and the heater blows lukewarm.", "Le thermostat reste ouvert : le moteur chauffe lentement, consomme plus et le chauffage reste tiède."),
            "thermostat", "coolantSensor")
        put("P0180", "Fuel temperature sensor circuit", "Circuit de la sonde de température du gazole", Severity.WATCH, SENSOR_WIRING)
        put("P0190", "Fuel rail pressure sensor circuit", "Circuit du capteur de pression de rampe", Severity.FIX, SENSOR_WIRING, "railSensor")
        put("P0191", "Fuel rail pressure sensor range / performance", "Capteur de pression de rampe : plage ou fonctionnement", Severity.FIX, SENSOR_WIRING, "railSensor")
        put("P0192", "Fuel rail pressure sensor circuit low", "Capteur de pression de rampe : signal bas", Severity.FIX, SENSOR_WIRING, "railSensor")
        put("P0193", "Fuel rail pressure sensor circuit high", "Capteur de pression de rampe : signal haut", Severity.FIX, SENSOR_WIRING, "railSensor")
        put("P0200", "Injector circuit", "Circuit d'injecteur", Severity.FIX, SENSOR_WIRING, "injector")
        for (cyl in 1..4) {
            put("P020$cyl", "Injector circuit, cylinder $cyl", "Circuit de l'injecteur du cylindre $cyl", Severity.FIX, SENSOR_WIRING, "injector")
        }
        put("P0217", "Engine overheating", "Surchauffe moteur", Severity.STOP,
            t("Stop the engine. Check the coolant level once cold, the fan, the water pump and the thermostat.", "Couper le moteur. Vérifier le niveau de liquide une fois froid, le ventilateur, la pompe à eau et le thermostat."),
            "thermostat", "waterPump", "coolantSensor")
        put("P0219", "Engine overspeed", "Surrégime moteur", Severity.WATCH)
        put("P0234", "Turbo overboost", "Suralimentation trop forte", Severity.FIX,
            t("Turbo vanes stuck closed, the turbo control solenoid or its vacuum hoses, or the boost sensor.", "Ailettes du turbo grippées en position fermée, électrovanne du turbo ou ses durites de dépression, ou capteur de pression."),
            "turboSolenoid", "boostSensor")
        put("P0235", "Turbo boost sensor A circuit", "Circuit du capteur de pression turbo", Severity.FIX, SENSOR_WIRING, "boostSensor")
        put("P0236", "Turbo boost sensor A range / performance", "Capteur de pression turbo : plage ou fonctionnement", Severity.FIX, UNDERBOOST, "boostSensor", "turboSolenoid")
        put("P0237", "Turbo boost sensor A circuit low", "Capteur de pression turbo : signal bas", Severity.FIX, SENSOR_WIRING, "boostSensor")
        put("P0238", "Turbo boost sensor A circuit high", "Capteur de pression turbo : signal haut", Severity.FIX, SENSOR_WIRING, "boostSensor")
        put("P0243", "Turbo wastegate solenoid A", "Électrovanne de régulation du turbo", Severity.FIX, SENSOR_WIRING, "turboSolenoid")
        for (cyl in 1..4) {
            put("P0${263 + (cyl - 1) * 3}", "Cylinder $cyl contribution / balance", "Contribution / équilibrage du cylindre $cyl", Severity.FIX,
                t("That cylinder's injector delivers too much or too little: have the injectors' correction values and leak-off tested.", "L'injecteur de ce cylindre débite trop ou pas assez : faire mesurer les corrections et le débit de retour des injecteurs."),
                "injector")
        }
        put("P0299", "Turbo underboost", "Manque de suralimentation (turbo)", Severity.FIX, UNDERBOOST, "turboSolenoid", "boostSensor")
        put("P0335", "Crankshaft position sensor circuit", "Circuit du capteur de régime vilebrequin", Severity.FIX, SENSOR_WIRING, "crankSensor")
        put("P0336", "Crankshaft position sensor range / performance", "Capteur vilebrequin : plage ou fonctionnement", Severity.FIX, SENSOR_WIRING, "crankSensor")
        put("P0340", "Camshaft position sensor circuit", "Circuit du capteur d'arbre à cames", Severity.FIX, SENSOR_WIRING, "camSensor")
        put("P0341", "Camshaft position sensor range / performance", "Capteur d'arbre à cames : plage ou fonctionnement", Severity.FIX, SENSOR_WIRING, "camSensor")
        put("P0380", "Glow plug circuit", "Circuit de préchauffage", Severity.FIX, GLOW, "glowPlug", "glowTimer")
        put("P0381", "Glow plug indicator circuit", "Circuit du témoin de préchauffage", Severity.WATCH, GLOW, "glowTimer")
        put("P0382", "Glow plug circuit B", "Circuit de préchauffage B", Severity.FIX, GLOW, "glowPlug", "glowTimer")
        put("P0400", "EGR flow", "Débit EGR", Severity.FIX, EGR, "egrValve")
        put("P0401", "EGR flow insufficient", "Débit EGR insuffisant", Severity.FIX, EGR, "egrValve", "egrCooler")
        put("P0402", "EGR flow excessive", "Débit EGR excessif", Severity.FIX, EGR, "egrValve")
        put("P0403", "EGR circuit", "Circuit de la vanne EGR", Severity.FIX, EGR, "egrValve")
        put("P0404", "EGR range / performance", "Vanne EGR : plage ou fonctionnement", Severity.FIX, EGR, "egrValve")
        put("P0405", "EGR position sensor circuit low", "Capteur de position EGR : signal bas", Severity.FIX, EGR, "egrValve")
        put("P0406", "EGR position sensor circuit high", "Capteur de position EGR : signal haut", Severity.FIX, EGR, "egrValve")
        put("P0409", "EGR sensor circuit", "Circuit du capteur EGR", Severity.FIX, EGR, "egrValve")
        put("P0420", "Catalyst efficiency below threshold", "Efficacité du catalyseur insuffisante", Severity.WATCH, null, "catalyst")
        put("P0480", "Cooling fan 1 control circuit", "Commande du ventilateur 1", Severity.FIX)
        put("P0487", "EGR throttle control circuit", "Commande du boîtier papillon (EGR)", Severity.FIX, SENSOR_WIRING, "throttle")
        put("P0488", "EGR throttle control range / performance", "Boîtier papillon : plage ou fonctionnement", Severity.FIX,
            t("The throttle body (intake air flap) is sooted up or its motor is failing: clean it, else replace it.", "Le boîtier papillon (doseur d'air) est encrassé ou son moteur faiblit : le nettoyer, sinon le remplacer."), "throttle")
        put("P0500", "Vehicle speed sensor", "Capteur de vitesse du véhicule", Severity.WATCH,
            t("Speed comes from the ABS wheel sensors: check the ABS for its own codes.", "La vitesse vient des capteurs de roue de l'ABS : chercher aussi des codes ABS."))
        put("P0520", "Oil pressure sensor / switch circuit", "Circuit du capteur de pression d'huile", Severity.FIX, SENSOR_WIRING)
        put("P0524", "Engine oil pressure too low", "Pression d'huile trop basse", Severity.STOP,
            t("Stop the engine and check the oil level now.", "Couper le moteur et vérifier tout de suite le niveau d'huile."), "oilFilter")
        put("P0560", "System voltage", "Tension du réseau de bord", Severity.WATCH, VOLTAGE, "battery", "alternator")
        put("P0562", "System voltage low", "Tension du réseau de bord trop basse", Severity.FIX, VOLTAGE, "battery", "alternator")
        put("P0563", "System voltage high", "Tension du réseau de bord trop haute", Severity.FIX, VOLTAGE, "alternator")
        put("P0571", "Brake switch circuit", "Circuit du contacteur de frein", Severity.WATCH)
        put("P0600", "ECU communication bus", "Liaison du calculateur moteur", Severity.FIX)
        put("P0601", "ECU memory checksum", "Mémoire du calculateur", Severity.FIX)
        put("P0606", "ECU processor", "Processeur du calculateur moteur", Severity.FIX)
        put("P0627", "Fuel pump control circuit", "Commande de la pompe à carburant", Severity.FIX, SENSOR_WIRING)
        put("P0641", "Sensor reference voltage A", "Tension d'alimentation des capteurs A", Severity.FIX,
            t("One faulty sensor can pull the shared 5 V supply down: unplug the sensors one by one.", "Un capteur défectueux peut faire chuter l'alimentation 5 V partagée : les débrancher un par un."))
        put("P0651", "Sensor reference voltage B", "Tension d'alimentation des capteurs B", Severity.FIX,
            t("One faulty sensor can pull the shared 5 V supply down: unplug the sensors one by one.", "Un capteur défectueux peut faire chuter l'alimentation 5 V partagée : les débrancher un par un."))
        put("P0670", "Glow plug control module", "Boîtier de préchauffage", Severity.FIX, GLOW, "glowTimer", "glowPlug")
        for (cyl in 1..4) {
            put("P067$cyl", "Glow plug, cylinder $cyl", "Bougie de préchauffage du cylindre $cyl", Severity.FIX, GLOW, "glowPlug")
        }
        put("P0683", "Glow plug module communication", "Communication avec le boîtier de préchauffage", Severity.FIX, GLOW, "glowTimer")
        put("P2002", "Particulate filter efficiency below threshold", "Efficacité du filtre à particules insuffisante", Severity.FIX, DPF, "catalyst", "dpfSensor")
        put("P2004", "Intake runner (swirl) control stuck open", "Volets de turbulence bloqués ouverts", Severity.WATCH, SWIRL, "intakeManifold")
        put("P2005", "Intake runner (swirl) control stuck open, bank 2", "Volets de turbulence bloqués ouverts (banc 2)", Severity.WATCH, SWIRL, "intakeManifold")
        put("P2006", "Intake runner (swirl) control stuck closed", "Volets de turbulence bloqués fermés", Severity.WATCH, SWIRL, "intakeManifold")
        put("P2008", "Intake runner (swirl) control circuit", "Circuit des volets de turbulence", Severity.WATCH, SWIRL, "intakeManifold")
        put("P2009", "Intake runner (swirl) control circuit low", "Volets de turbulence : signal bas", Severity.WATCH, SWIRL, "intakeManifold")
        put("P2015", "Intake runner (swirl) position sensor range", "Capteur de position des volets de turbulence", Severity.WATCH, SWIRL, "intakeManifold")
        put("P2031", "Exhaust temperature sensor 2", "Sonde de température d'échappement 2", Severity.WATCH, SENSOR_WIRING)
        put("P2080", "Exhaust temperature sensor 1 range", "Sonde de température d'échappement 1", Severity.WATCH, SENSOR_WIRING)
        put("P2100", "Throttle actuator control motor circuit", "Moteur du boîtier papillon", Severity.FIX, SENSOR_WIRING, "throttle")
        put("P2101", "Throttle actuator control motor range", "Moteur du boîtier papillon : plage", Severity.FIX, SENSOR_WIRING, "throttle")
        put("P2135", "Throttle position sensors correlation", "Corrélation des capteurs de position du papillon", Severity.FIX, SENSOR_WIRING, "throttle")
        put("P2138", "Accelerator pedal sensors correlation", "Corrélation des capteurs de la pédale d'accélérateur", Severity.FIX, SENSOR_WIRING)
        put("P2146", "Injector group A supply", "Alimentation des injecteurs (groupe A)", Severity.FIX, SENSOR_WIRING, "injector")
        put("P2149", "Injector group B supply", "Alimentation des injecteurs (groupe B)", Severity.FIX, SENSOR_WIRING, "injector")
        put("P2263", "Turbo boost system performance", "Système de suralimentation : fonctionnement", Severity.FIX, UNDERBOOST, "turboSolenoid", "boostSensor")
        put("P2264", "Water-in-fuel sensor circuit", "Circuit du détecteur d'eau dans le gazole", Severity.WATCH, SENSOR_WIRING, "fuelFilter")
        put("P2269", "Water in fuel", "Présence d'eau dans le gazole", Severity.FIX,
            t("Drain the fuel filter's water (or replace the filter) soon: water damages the injection pump and injectors.", "Purger l'eau du filtre à gazole (ou le remplacer) rapidement : l'eau abîme la pompe et les injecteurs."), "fuelFilter")
        put("P2413", "EGR system performance", "Système EGR : fonctionnement", Severity.FIX, EGR, "egrValve")
        put("P244A", "Particulate filter differential pressure too low", "Pression différentielle du FAP trop basse", Severity.FIX,
            t("A disconnected or split hose on the DPF pressure sensor, the sensor itself, or a cracked filter.", "Une durite du capteur de pression du FAP débranchée ou fendue, le capteur, ou un filtre fissuré."), "dpfSensor", "catalyst")
        put("P244B", "Particulate filter differential pressure too high", "Pression différentielle du FAP trop haute", Severity.FIX, DPF, "catalyst", "dpfSensor")
        put("P2452", "Particulate filter pressure sensor A circuit", "Circuit du capteur de pression du FAP", Severity.FIX, SENSOR_WIRING, "dpfSensor")
        put("P2453", "Particulate filter pressure sensor A range", "Capteur de pression du FAP : plage", Severity.FIX,
            t("Check the sensor's two small hoses (blocked, swapped or split), then the sensor.", "Vérifier les deux petites durites du capteur (bouchées, inversées ou fendues), puis le capteur."), "dpfSensor")
        put("P2454", "Particulate filter pressure sensor A low", "Capteur de pression du FAP : signal bas", Severity.FIX, SENSOR_WIRING, "dpfSensor")
        put("P2455", "Particulate filter pressure sensor A high", "Capteur de pression du FAP : signal haut", Severity.FIX, SENSOR_WIRING, "dpfSensor")
        put("P2458", "Particulate filter regeneration duration", "Durée de régénération du FAP", Severity.WATCH, DPF, "catalyst")
        put("P2459", "Particulate filter regeneration frequency", "Fréquence de régénération du FAP", Severity.WATCH, DPF, "catalyst", "dpfSensor")
        put("P2463", "Particulate filter soot accumulation", "Filtre à particules chargé en suie", Severity.FIX, DPF, "catalyst", "dpfSensor")
        put("P2562", "Turbo boost control position sensor circuit", "Capteur de position de commande du turbo", Severity.FIX, SENSOR_WIRING, "turboSolenoid")
        put("P2563", "Turbo boost control position sensor range", "Capteur de position du turbo : plage", Severity.FIX, UNDERBOOST, "turboSolenoid")
        put("U0001", "High-speed CAN communication bus", "Bus CAN haute vitesse", Severity.FIX)
        put("U0100", "Lost communication with engine ECU", "Perte de communication avec le calculateur moteur", Severity.FIX)
        put("U0121", "Lost communication with ABS", "Perte de communication avec l'ABS", Severity.FIX)
        put("U0155", "Lost communication with instrument cluster", "Perte de communication avec le combiné", Severity.WATCH)
    }

    private fun family(code: String): Text {
        val letter = code.firstOrNull() ?: 'P'
        val second = code.getOrNull(1) ?: '0'
        val third = code.getOrNull(2) ?: '0'
        if (letter == 'C') return t("Chassis (ABS, ESP, 4x4)", "Châssis (ABS, ESP, 4x4)")
        if (letter == 'B') return t("Body (airbags, lights, comfort)", "Carrosserie (airbags, éclairage, confort)")
        if (letter == 'U') return t("Network (communication between ECUs)", "Réseau (communication entre calculateurs)")
        if (second == '1' || second == '3') return t("Engine, manufacturer-specific code", "Moteur, code propre au constructeur")
        return when (third) {
            '0' -> t("Fuel, air and auxiliary emission controls", "Dosage air / carburant et antipollution")
            '1', '2' -> t("Fuel and air metering", "Dosage air / carburant")
            '3' -> t("Ignition / glow plugs / engine position", "Allumage, préchauffage, position moteur")
            '4' -> t("Emission controls (EGR, catalyst, DPF)", "Antipollution (EGR, catalyseur, FAP)")
            '5' -> t("Speed, idle and auxiliary inputs", "Vitesse, ralenti et entrées auxiliaires")
            '6' -> t("Engine ECU and its outputs", "Calculateur moteur et ses sorties")
            '7', '8', '9' -> t("Transmission", "Transmission")
            'A', 'B', 'C' -> t("Hybrid propulsion", "Propulsion hybride")
            else -> t("Engine", "Moteur")
        }
    }

    fun info(code: String): DtcInfo {
        val c = code.uppercase()
        val e = table[c]
        return if (e != null) {
            DtcInfo(c, e.title, family(c), e.severity, e.advice, e.parts)
        } else {
            DtcInfo(
                c,
                t("Code not in the app's table yet", "Code pas encore dans le tableau de l'appli"),
                family(c),
                Severity.WATCH,
                if (c.getOrNull(1) == '1') t(
                    "A Nissan / Renault-specific code: its exact meaning needs the manufacturer's documentation (send it in the diagnostics and it will be added).",
                    "Code propre à Nissan / Renault : son sens exact demande la documentation du constructeur (envoyez-le dans les diagnostics, il sera ajouté).",
                ) else null,
                known = false,
            )
        }
    }

    val count: Int get() = table.size
}
