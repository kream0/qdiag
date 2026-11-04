package app.qdiag.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.Graph
import app.qdiag.core.health.Finding
import app.qdiag.core.health.Level
import app.qdiag.local
import app.qdiag.tr
import app.qdiag.ui.components.Card
import app.qdiag.ui.components.Dot
import app.qdiag.ui.components.PartLinks
import app.qdiag.ui.components.ScreenHeader
import app.qdiag.ui.components.SectionLabel
import app.qdiag.ui.components.levelColor
import app.qdiag.ui.components.timeAgo
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type

@Composable
fun HealthScreen() {
    val codes by Graph.car.codes.collectAsStateWithLifecycle()
    val findings by Graph.car.findings.collectAsStateWithLifecycle()
    val live by Graph.car.live.collectAsStateWithLifecycle()
    val history by Graph.store.history.collectAsStateWithLifecycle()
    val readiness = remember(codes) { Graph.car.readiness() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(tr("CHECK-UP", "BILAN"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(tr("Contrôle technique (emissions & light)", "Contrôle technique (pollution et voyant)"))
            if (readiness.isEmpty()) {
                Text(
                    tr("Read the codes (Codes tab) to see whether the car is ready.", "Lisez les codes (onglet Codes) pour voir si la voiture est prête."),
                    style = Type.body, color = P.textDim,
                )
            } else {
                val worst = readiness.maxOf { it.level.ordinal }
                Text(
                    when (Level.entries[worst]) {
                        Level.WARN -> tr("Not ready: fix what's in red first.", "Pas prête : réglez d'abord ce qui est en rouge.")
                        Level.WATCH -> tr("Almost: see the points in yellow.", "Presque : voyez les points en jaune.")
                        else -> tr("Looks ready on the electronic side.", "Côté électronique, elle semble prête.")
                    },
                    style = Type.titleLarge, color = levelColor(Level.entries[worst]),
                )
                for (f in readiness) FindingCard(f)
                Text(
                    tr(
                        "The opacity test (smoke) is measured by the test centre, not by the ECU: a regenerated DPF and a warm engine help.",
                        "L'opacité (fumées) est mesurée par le centre, pas par le calculateur : un FAP régénéré et un moteur chaud aident.",
                    ),
                    style = Type.body, color = P.textFaint,
                )
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(tr("Engine health (live session)", "Santé moteur (session en direct)"))
            if (findings.isEmpty()) {
                Text(
                    if (live.running) tr("Collecting values…", "Collecte des valeurs…")
                    else tr("Run a live session (Live tab) during a drive to check the engine.", "Lancez une session en direct (onglet Direct) pendant un trajet pour vérifier le moteur."),
                    style = Type.body, color = P.textDim,
                )
            } else {
                for (f in findings.sortedByDescending { it.level.ordinal }) FindingCard(f)
                Text(
                    tr(
                        "Thresholds are indicative, set for a healthy 2.0 dCi: a finding says what to look at, not what to replace. Confirm with a garage before buying parts.",
                        "Les seuils sont indicatifs, réglés pour un 2.0 dCi en bon état : un constat dit quoi regarder, pas quoi remplacer. Faites confirmer par un garage avant d'acheter des pièces.",
                    ),
                    style = Type.body, color = P.textFaint,
                )
            }

            if (history.drives.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SectionLabel(tr("Previous sessions", "Sessions précédentes"))
                Card {
                    for (d in history.drives.take(12)) {
                        val issues = d.findings.filterValues { it == "WARN" || it == "WATCH" }
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(
                                when {
                                    "WARN" in issues.values -> P.warn
                                    issues.isNotEmpty() -> P.watch
                                    else -> P.good
                                },
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(timeAgo(d.time) + " · %.0f min".format(d.minutes), style = Type.label, color = P.textDim)
                                Text(
                                    if (issues.isEmpty()) tr("Nothing to report", "Rien à signaler")
                                    else issues.keys.joinToString(", ") { checkName(it) },
                                    style = Type.body, color = P.text,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun checkName(id: String): String = when (id) {
    "battery" -> tr("battery", "batterie")
    "charging" -> tr("charging", "charge")
    "warmup" -> tr("warm-up", "montée en température")
    "overheat" -> tr("temperature", "température")
    "boost" -> "turbo"
    "rail" -> tr("rail pressure", "pression rail")
    "maf" -> tr("air flow", "débit d'air")
    "egr" -> "EGR"
    else -> id
}

@Composable
private fun FindingCard(f: Finding) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(levelColor(f.level))
            Spacer(Modifier.width(10.dp))
            Text(f.title.local, style = Type.title, color = P.text)
        }
        Spacer(Modifier.height(4.dp))
        Text(f.detail.local, style = Type.body, color = P.textDim)
        if (f.parts.isNotEmpty() && f.level != Level.GOOD) {
            Spacer(Modifier.height(8.dp))
            PartLinks(f.parts)
        }
    }
}
