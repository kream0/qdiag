package app.qdiag.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.Graph
import app.qdiag.car.LinkState
import app.qdiag.core.elm.Discovery
import app.qdiag.tr
import app.qdiag.ui.components.Field
import app.qdiag.ui.components.PillButton
import app.qdiag.ui.components.PillStyle
import app.qdiag.ui.components.ScreenHeader
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type

private val quick = listOf("ATRV", "ATDPN", "ATI", "0100", "0101", "03", "07", "0902", "090A")

@Composable
fun ConsoleScreen() {
    val link by Graph.car.link.collectAsStateWithLifecycle()
    val log by Graph.car.console.collectAsStateWithLifecycle()
    val busy by Graph.car.busy.collectAsStateWithLifecycle()
    val connected = link.state == LinkState.READY
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.animateScrollToItem(log.size - 1) }

    fun send(cmd: String) {
        when (Graph.car.send(cmd)) {
            null -> input = ""
            "OFFLINE" -> Graph.toast(tr("Not connected", "Non connecté"))
            "HEX" -> Graph.toast(tr("Type an AT command or hex bytes", "Tapez une commande AT ou des octets en hexadécimal"))
            "WRITE" -> Graph.toast(
                tr("Blocked: this request changes something in the car (Settings → Expert console)", "Bloqué : cette requête modifie la voiture (Réglages → Console experte)"),
            )
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(tr("CONSOLE", "CONSOLE")) {
            Text(
                tr("CLEAR", "VIDER"), style = Type.labelBold, color = P.textDim,
                modifier = Modifier.clip(CircleShape).clickable { Graph.car.clearConsole() }.padding(10.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            PillButton(
                if (busy != null) tr("Survey running…", "Relevé en cours…") else tr("Full survey for the developer", "Relevé complet pour le développeur"),
                { Graph.car.runDiscovery() }, Modifier.fillMaxWidth(),
                style = PillStyle.Accent, enabled = connected && busy == null,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr(
                    "Reads (never writes) what the engine ECU can tell beyond the standard values, about ${Discovery.all.size} requests, and sends it in the diagnostics so the app can learn this car. Engine running or ignition on, about a minute.",
                    "Lit (sans jamais écrire) ce que le calculateur sait dire au-delà des valeurs standard, environ ${Discovery.all.size} requêtes, et l'envoie dans les diagnostics pour que l'appli apprenne cette voiture. Moteur tournant ou contact mis, environ une minute.",
                ),
                style = Type.body, color = P.textFaint,
            )
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = list,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (log.isEmpty()) {
                item {
                    Text(
                        tr("Every exchange with the adapter shows here.", "Chaque échange avec l'adaptateur s'affiche ici."),
                        style = Type.body, color = P.textDim,
                    )
                }
            }
            items(log) { e ->
                Column {
                    Text("> ${e.command}  ·  ${e.ms} ms", style = Type.mono, color = P.accent)
                    Text(
                        Discovery.mask(e.reply.replace("\r", "\n").replace(">", "").trim().ifEmpty { "(—)" }),
                        style = Type.mono, color = P.text,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (q in quick) {
                Text(
                    q, style = Type.labelBold, color = if (connected) P.text else P.textFaint,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = connected) { send(q) }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(input, { input = it.uppercase() }, Modifier.weight(1f), placeholder = "ATRV · 010C · 0902", caps = true, onDone = { send(input) })
            PillButton(tr("Send", "Envoyer"), { send(input) }, enabled = connected && input.isNotBlank())
        }
    }
}
