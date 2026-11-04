package app.qdiag.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.Graph
import app.qdiag.car.LinkState
import app.qdiag.core.kb.Dtcs
import app.qdiag.local
import app.qdiag.tr
import app.qdiag.ui.components.Card
import app.qdiag.ui.components.Dot
import app.qdiag.ui.components.PartLinks
import app.qdiag.ui.components.PillButton
import app.qdiag.ui.components.PillStyle
import app.qdiag.ui.components.ScreenHeader
import app.qdiag.ui.components.SectionLabel
import app.qdiag.ui.components.severityColor
import app.qdiag.ui.components.timeAgo
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type

@Composable
fun CodesScreen() {
    val link by Graph.car.link.collectAsStateWithLifecycle()
    val codes by Graph.car.codes.collectAsStateWithLifecycle()
    val history by Graph.store.history.collectAsStateWithLifecycle()
    val connected = link.state == LinkState.READY
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(tr("CODES", "CODES"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    if (codes.busy) tr("Reading…", "Lecture…") else tr("Read codes", "Lire les codes"),
                    { Graph.car.readCodes() }, Modifier.weight(1f),
                    style = PillStyle.Accent, enabled = connected && !codes.busy,
                )
                PillButton(tr("Clear", "Effacer"), { confirmClear = true }, Modifier.weight(1f), enabled = connected && !codes.busy && codes.readAt != null)
            }
            if (!connected) {
                Text(tr("Connect to the car on the Home tab first.", "Connectez-vous d'abord à la voiture depuis l'Accueil."), style = Type.body, color = P.textDim)
            }

            if (confirmClear) {
                Card {
                    SectionLabel(tr("Clear the fault codes?", "Effacer les codes défaut ?"), color = P.warn)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(
                            "This turns the check-engine light off and erases the codes and their freeze-frame data. It also resets the readiness self-tests: until a mixed drive of ~50 km completes them, the contrôle technique sees an ECU that was just cleared. If the fault is still there the code comes back. Clear only after a repair.",
                            "Cela éteint le voyant moteur et efface les codes et leurs données figées. Cela remet aussi à zéro les autotests : tant qu'un trajet mixte d'environ 50 km ne les a pas refaits, le contrôle technique voit un calculateur tout juste effacé. Si le défaut est toujours là, le code revient. N'effacez qu'après une réparation.",
                        ),
                        style = Type.body, color = P.textDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(tr("Cancel", "Annuler"), { confirmClear = false }, Modifier.weight(1f))
                        PillButton(tr("Clear codes", "Effacer"), { confirmClear = false; Graph.car.clearCodes() }, Modifier.weight(1f), style = PillStyle.Accent)
                    }
                }
            }

            if (codes.readAt != null) {
                val multiEcu = (codes.stored.keys + codes.pending.keys + codes.permanent.keys).size > 1
                Text(tr("Read ", "Lus ") + timeAgo(codes.readAt!!), style = Type.label, color = P.textFaint)
                codes.monitors?.let { m ->
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(if (m.milOn) P.warn else P.good)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (m.milOn) tr("Check-engine light ON", "Voyant moteur ALLUMÉ") else tr("Check-engine light off", "Voyant moteur éteint"),
                                style = Type.title, color = P.text,
                            )
                        }
                        Text(
                            tr("The ECU reports ${m.dtcCount} confirmed code(s).", "Le calculateur signale ${m.dtcCount} code(s) confirmé(s)."),
                            style = Type.body, color = P.textDim,
                        )
                    }
                }
                CodeGroup(tr("Stored", "Enregistrés"), codes.stored, multiEcu, tr("Confirmed fault: the ECU saw it on consecutive drives.", "Défaut confirmé : vu par le calculateur sur plusieurs trajets."))
                CodeGroup(tr("Pending", "En attente"), codes.pending, multiEcu, tr("Seen once, not confirmed yet. Worth watching.", "Vu une fois, pas encore confirmé. À surveiller."))
                CodeGroup(tr("Permanent", "Permanents"), codes.permanent, multiEcu, tr("Can't be cleared: they go away once the ECU sees the fault is fixed.", "Ne s'effacent pas : ils disparaissent quand le calculateur constate la réparation."))
                if (codes.storedList.isEmpty() && codes.pendingList.isEmpty() && codes.permanentList.isEmpty()) {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(P.good)
                            Spacer(Modifier.width(10.dp))
                            Text(tr("No fault code", "Aucun code défaut"), style = Type.title, color = P.text)
                        }
                        Text(
                            tr("The engine ECU has nothing stored or pending.", "Le calculateur moteur n'a rien d'enregistré ni en attente."),
                            style = Type.body, color = P.textDim,
                        )
                    }
                }
            } else {
                history.reads.firstOrNull { !it.cleared }?.let { last ->
                    Card {
                        SectionLabel(tr("Last reading", "Dernière lecture"))
                        Spacer(Modifier.height(6.dp))
                        Text(timeAgo(last.time), style = Type.label, color = P.textFaint)
                        val all = last.stored + last.pending
                        if (all.isEmpty()) Text(tr("No fault code", "Aucun code défaut"), style = Type.title, color = P.text)
                        for (code in last.stored) CodeLine(code, pending = false)
                        for (code in last.pending) CodeLine(code, pending = true)
                    }
                }
            }

            Card {
                SectionLabel(tr("About this list", "À propos"))
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(
                        "Meanings and likely causes are written for the 2.0 dCi (M9R) and its usual weak points; ${Dtcs.count} codes are described. Codes starting with P1 are Nissan/Renault's own: their meaning is sent in the diagnostics so a future version can describe them. Part numbers are Nissan's: tap one to compare prices.",
                        "Les explications et causes probables sont rédigées pour le 2.0 dCi (M9R) et ses points faibles connus ; ${Dtcs.count} codes sont décrits. Les codes en P1 sont propres à Nissan/Renault : ils partent dans les diagnostics pour qu'une version future les décrive. Les références sont celles de Nissan : touchez-en une pour comparer les prix.",
                    ),
                    style = Type.body, color = P.textDim,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CodeGroup(title: String, byEcu: Map<String, List<String>>, multiEcu: Boolean, help: String) {
    val list = byEcu.flatMap { (ecu, codes) -> codes.map { ecu to it } }.distinctBy { it.second }
    if (list.isEmpty()) return
    SectionLabel("$title · ${list.size}")
    Text(help, style = Type.body, color = P.textFaint)
    for ((ecu, code) in list) CodeCard(code, if (multiEcu) ecuName(ecu) else null)
}

private fun ecuName(header: String): String = when (header.uppercase()) {
    "7E8" -> tr("engine", "moteur")
    "7E9" -> tr("gearbox", "boîte")
    "?" -> "ECU"
    else -> "ECU $header"
}

@Composable
private fun CodeCard(code: String, ecu: String?) {
    val info = Dtcs.info(code)
    var open by remember { mutableStateOf(true) }
    Card(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(code, style = Type.displaySmall, color = P.text)
            Spacer(Modifier.width(12.dp))
            Dot(severityColor(info.severity))
            Spacer(Modifier.width(6.dp))
            Text(info.severity.label.local.uppercase(), style = Type.label, color = severityColor(info.severity), modifier = Modifier.weight(1f))
            if (ecu != null) Text(ecu.uppercase(), style = Type.label, color = P.textFaint)
        }
        Spacer(Modifier.height(6.dp))
        Text(info.title.local, style = Type.title, color = P.text)
        Text(info.system.local, style = Type.label, color = P.textDim)
        if (open) {
            info.advice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.local, style = Type.body, color = P.text)
            }
            if (!info.known) {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Not in the app's table yet: only its family is known.", "Pas encore dans la table de l'appli : seule sa famille est connue."),
                    style = Type.body, color = P.textFaint,
                )
            }
            if (info.parts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                PartLinks(info.parts)
            }
        }
    }
}

@Composable
private fun CodeLine(code: String, pending: Boolean) {
    val info = Dtcs.info(code)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(severityColor(info.severity))
        Spacer(Modifier.width(8.dp))
        Text(code + if (pending) "*" else "", style = Type.labelBold, color = P.text, modifier = Modifier.width(64.dp))
        Text(info.title.local, style = Type.body, color = P.textDim)
    }
}
