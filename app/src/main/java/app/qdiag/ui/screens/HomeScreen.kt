package app.qdiag.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.Graph
import app.qdiag.car.LinkState
import app.qdiag.car.LinkStatus
import app.qdiag.core.elm.Discovery
import app.qdiag.tr
import app.qdiag.ui.Tab
import app.qdiag.ui.components.Card
import app.qdiag.ui.components.Dot
import app.qdiag.ui.components.KeyValue
import app.qdiag.ui.components.PillButton
import app.qdiag.ui.components.PillStyle
import app.qdiag.ui.components.ScreenHeader
import app.qdiag.ui.components.SectionLabel
import app.qdiag.ui.components.timeAgo
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type

@Composable
fun HomeScreen(onTab: (Tab) -> Unit, onSettings: () -> Unit) {
    val link by Graph.car.link.collectAsStateWithLifecycle()
    val info by Graph.car.info.collectAsStateWithLifecycle()
    val codes by Graph.car.codes.collectAsStateWithLifecycle()
    val history by Graph.store.history.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("QDIAG") {
            Text(
                tr("SETTINGS", "RÉGLAGES"), style = Type.labelBold, color = P.textDim,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onSettings).padding(10.dp),
            )
        }
        Text(
            "NISSAN QASHQAI+2 · 2.0 dCi M9R",
            style = Type.label, color = P.textFaint, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(
                        when (link.state) {
                            LinkState.READY -> P.good
                            LinkState.FAILED -> P.warn
                            LinkState.IDLE -> P.textFaint
                            else -> P.watch
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(statusTitle(link), style = Type.title, color = P.text, modifier = Modifier.weight(1f))
                }
                statusHelp(link, settings.ssidPrefix)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = Type.body, color = P.textDim)
                }
                if (info.adapter != null) {
                    Spacer(Modifier.height(10.dp))
                    KeyValue(tr("Adapter", "Adaptateur"), info.adapter ?: "")
                    info.protocol?.let { KeyValue(tr("Protocol", "Protocole"), it) }
                    info.voltage?.let { KeyValue(tr("Battery", "Batterie"), "%.1f V".format(it)) }
                    info.vin?.let { KeyValue("VIN", Discovery.mask(it)) }
                    info.ecuName?.let { KeyValue(tr("Engine ECU", "Calculateur"), it) }
                    if (info.supported.isNotEmpty()) KeyValue(tr("Live values", "Valeurs lisibles"), "${info.supported.size} PID")
                }
                Spacer(Modifier.height(14.dp))
                when (link.state) {
                    LinkState.READY -> PillButton(tr("Disconnect", "Déconnecter"), { Graph.car.disconnect() }, Modifier.fillMaxWidth())
                    LinkState.JOINING, LinkState.CONNECTING -> PillButton(tr("Cancel", "Annuler"), { Graph.car.disconnect() }, Modifier.fillMaxWidth())
                    else -> PillButton(tr("Connect", "Se connecter"), { Graph.car.connect() }, Modifier.fillMaxWidth(), style = PillStyle.Accent)
                }
            }

            if (codes.readAt != null) {
                Card(onClick = { onTab(Tab.CODES) }) {
                    SectionLabel(tr("Fault codes", "Codes défaut"))
                    Spacer(Modifier.height(8.dp))
                    val n = codes.storedList.size
                    val pending = codes.pendingList.size
                    Text(
                        when {
                            n == 0 && pending == 0 -> tr("No fault code", "Aucun code défaut")
                            n == 0 -> tr("$pending pending code(s)", "$pending code(s) en attente")
                            else -> tr("$n stored code(s)", "$n code(s) enregistré(s)") + if (pending > 0) tr(" · $pending pending", " · $pending en attente") else ""
                        },
                        style = Type.titleLarge, color = if (n > 0) P.warn else P.text,
                    )
                    codes.monitors?.let { m ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (m.milOn) tr("Check-engine light ON", "Voyant moteur ALLUMÉ") else tr("Check-engine light off", "Voyant moteur éteint"),
                            style = Type.body, color = if (m.milOn) P.warn else P.textDim,
                        )
                    }
                    Text(timeAgo(codes.readAt!!), style = Type.label, color = P.textFaint)
                }
            }

            if (link.state == LinkState.READY) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(tr("Live data", "Données"), { onTab(Tab.LIVE) }, Modifier.weight(1f))
                    PillButton(tr("Check-up", "Bilan"), { onTab(Tab.HEALTH) }, Modifier.weight(1f))
                }
            }

            Card {
                SectionLabel(tr("How to connect", "Pour se connecter"))
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "1. Plug the adapter into the OBD socket under the dashboard, left of the steering column.\n" +
                            "2. Ignition on (engine running for live data).\n" +
                            "3. Tap Connect: Android offers to join the adapter's Wi-Fi (\"${settings.ssidPrefix}…\"). Mobile data stays on for the internet.\n" +
                            "If it doesn't show up, join the adapter's Wi-Fi in Android's settings, then come back and tap Connect.",
                        "1. Branchez l'adaptateur sur la prise OBD sous le tableau de bord, à gauche de la colonne de direction.\n" +
                            "2. Contact mis (moteur tournant pour les données en direct).\n" +
                            "3. Appuyez sur Se connecter : Android propose de rejoindre le Wi-Fi de l'adaptateur (« ${settings.ssidPrefix}… »). Les données mobiles restent actives pour internet.\n" +
                            "S'il n'apparaît pas, rejoignez le Wi-Fi de l'adaptateur dans les réglages d'Android, puis revenez et appuyez sur Se connecter.",
                    ),
                    style = Type.body, color = P.textDim,
                )
            }

            if (history.reads.isNotEmpty() || history.drives.isNotEmpty()) {
                Card {
                    SectionLabel(tr("Logbook", "Historique"))
                    Spacer(Modifier.height(6.dp))
                    val events = (history.reads.map { it.time to readLine(it) } + history.drives.map { it.time to driveLine(it) })
                        .sortedByDescending { it.first }
                        .take(8)
                    for ((time, line) in events) {
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(timeAgo(time), style = Type.label, color = P.textFaint, modifier = Modifier.width(110.dp))
                            Text(line, style = Type.body, color = P.text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun readLine(r: app.qdiag.data.CodeRead): String = when {
    r.cleared -> tr("Codes cleared", "Codes effacés")
    r.stored.isEmpty() && r.pending.isEmpty() -> tr("Codes read: none", "Lecture des codes : aucun")
    else -> tr("Codes: ", "Codes : ") + (r.stored + r.pending.map { "$it*" }).joinToString(" ")
}

private fun driveLine(d: app.qdiag.data.DriveSummary): String {
    val issues = d.findings.count { it.value == "WARN" || it.value == "WATCH" }
    return tr("Live session %.0f min", "Session en direct %.0f min").format(d.minutes) +
        if (issues > 0) tr(" · $issues point(s) to check", " · $issues point(s) à vérifier") else ""
}

private fun statusTitle(link: LinkStatus): String = when (link.state) {
    LinkState.IDLE -> tr("Not connected", "Non connecté")
    LinkState.JOINING -> tr("Joining the adapter's Wi-Fi…", "Connexion au Wi-Fi de l'adaptateur…")
    LinkState.CONNECTING -> if (link.detail == "adapter") tr("Talking to the car…", "Dialogue avec la voiture…") else tr("Looking for the adapter…", "Recherche de l'adaptateur…")
    LinkState.READY -> tr("Connected to the car", "Connecté à la voiture")
    LinkState.FAILED -> tr("Connection failed", "Échec de la connexion")
}

private fun statusHelp(link: LinkStatus, ssid: String): String? = when (link.state) {
    LinkState.JOINING -> tr(
        "Pick the adapter's network (\"$ssid…\") in Android's prompt.",
        "Choisissez le réseau de l'adaptateur (« $ssid… ») dans la fenêtre d'Android.",
    )
    LinkState.FAILED -> when (link.detail) {
        "WIFI" -> tr(
            "The adapter didn't answer on Wi-Fi. Is it plugged in with its light on, and is the phone on its Wi-Fi? Check the address in Settings (usually 192.168.0.10:35000).",
            "L'adaptateur ne répond pas en Wi-Fi. Est-il branché, voyant allumé, et le téléphone est-il sur son Wi-Fi ? Vérifiez l'adresse dans Réglages (souvent 192.168.0.10:35000).",
        )
        "LOST" -> tr("The link to the adapter dropped. Tap Connect again.", "La liaison avec l'adaptateur a été coupée. Appuyez à nouveau sur Se connecter.")
        "NO_ECU", "UNABLE_TO_CONNECT", "NO_DATA", "BUS_ERROR", "CAN_ERROR" -> tr(
            "The adapter answers but the car doesn't. Turn the ignition on (or start the engine) and try again.",
            "L'adaptateur répond mais pas la voiture. Mettez le contact (ou démarrez) et réessayez.",
        )
        else -> tr("Unexpected error (${link.detail}). It was sent in the diagnostics.", "Erreur inattendue (${link.detail}). Elle a été envoyée dans les diagnostics.")
    }
    else -> null
}
