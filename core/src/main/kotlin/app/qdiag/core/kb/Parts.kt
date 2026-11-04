package app.qdiag.core.kb

import app.qdiag.core.Text

/** A part of the Qashqai+2 (JJ10E, M9R 2.0 dCi 150, 4WD, built 12/2008), with its Nissan number. */
data class Part(val key: String, val name: Text, val oem: String?, val note: Text? = null) {
    /** Autodoc's search for the OEM number, as in the logbook's parts tab. */
    val searchUrl: String? get() = oem?.let { "https://www.auto-doc.fr/search?keyword=$it" }
}

/**
 * Parts linked to fault codes and checks. Numbers come from Nissan's parts catalogue for this
 * car (frame JJ10E, engine M9R, production 12/2008, the same catalogue as the logbook's parts
 * tab). A part without a number is one the catalogue doesn't list for this car.
 */
object Parts {
    val all: List<Part> = listOf(
        Part("glowPlug", Text("Glow plugs (×4)", "Bougies de préchauffage (×4)"), "1106500Q0C"),
        Part("glowTimer", Text("Glow plug control unit", "Boîtier de préchauffage"), "11067JD50C"),
        Part("egrValve", Text("EGR valve", "Vanne EGR"), "1495600Q1A"),
        Part("egrCooler", Text("EGR cooler", "Refroidisseur EGR"), "1473500QAB"),
        Part("throttle", Text("Throttle body (intake air flap)", "Boîtier papillon (doseur d'air)"), "1471500Q0A"),
        Part("boostSensor", Text("Boost pressure sensor", "Capteur de pression de suralimentation"), "2373100Q0L"),
        Part("maf", Text("Mass air flow sensor", "Débitmètre d'air"), "226807S000"),
        Part("airFilter", Text("Air filter element", "Filtre à air"), "16546JD20B"),
        Part("fuelFilter", Text("Fuel filter", "Filtre à gazole"), "16400JD50A"),
        Part("railSensor", Text("Fuel rail pressure sensor", "Capteur de pression de rampe"), "1663800Q0F"),
        Part("railRegulator", Text("Fuel pressure regulator", "Régulateur de pression de carburant"), "2267000Q0A"),
        Part("injector", Text("Injector (nozzle and holder)", "Injecteur"), "1660000Q0A"),
        Part("injPump", Text("High-pressure injection pump", "Pompe haute pression"), "1670000Q1E"),
        Part("rail", Text("Common rail", "Rampe commune"), "1752000Q3F"),
        Part("coolantSensor", Text("Coolant temperature sensor", "Sonde de température du liquide de refroidissement"), "2263000Q0K"),
        Part("thermostat", Text("Thermostat", "Thermostat"), null, Text("Not listed by number for this car in the catalogue.", "Pas de numéro pour cette voiture dans le catalogue.")),
        Part("waterPump", Text("Water pump", "Pompe à eau"), "2101000Q0C"),
        Part("dpfSensor", Text("Exhaust (DPF) differential pressure sensor", "Capteur de pression différentielle du FAP"), "2373100Q0J"),
        Part("catalyst", Text("Catalytic converter / particulate filter", "Catalyseur / filtre à particules"), "208A0JD51A"),
        Part("crankSensor", Text("Crankshaft position sensor", "Capteur de régime vilebrequin"), "2373100Q0D"),
        Part("camSensor", Text("Camshaft (phase) sensor", "Capteur d'arbre à cames (phase)"), "2373100Q0C"),
        Part("turboSolenoid", Text("Turbo control solenoid valve", "Électrovanne de commande du turbo"), "14956BC40A"),
        Part("turboPump", Text("Turbo cooling pump", "Pompe de refroidissement du turbo"), "144B0JG70A"),
        Part("intakeManifold", Text("Intake manifold (swirl flaps)", "Collecteur d'admission (volets de turbulence)"), "1400400Q0H"),
        Part("alternator", Text("Alternator", "Alternateur"), "23100JD71A"),
        Part("starter", Text("Starter motor", "Démarreur"), "23300JG70A"),
        Part("battery", Text("Battery", "Batterie"), "24410JD13A"),
        Part("oilFilter", Text("Oil filter", "Filtre à huile"), "1520900Q0A"),
    )

    private val byKey = all.associateBy { it.key }

    operator fun get(key: String): Part? = byKey[key]
}
