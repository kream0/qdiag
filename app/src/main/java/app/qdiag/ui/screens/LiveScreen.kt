package app.qdiag.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.Graph
import app.qdiag.car.LinkState
import app.qdiag.core.health.Level
import app.qdiag.core.obd.PidGroup
import app.qdiag.core.obd.Signal
import app.qdiag.local
import app.qdiag.tr
import app.qdiag.ui.components.Card
import app.qdiag.ui.components.Dot
import app.qdiag.ui.components.PillButton
import app.qdiag.ui.components.PillStyle
import app.qdiag.ui.components.ScreenHeader
import app.qdiag.ui.components.SectionLabel
import app.qdiag.ui.components.levelColor
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type

@Composable
fun LiveScreen(onHealth: () -> Unit) {
    val link by Graph.car.link.collectAsStateWithLifecycle()
    val live by Graph.car.live.collectAsStateWithLifecycle()
    val findings by Graph.car.findings.collectAsStateWithLifecycle()
    val connected = link.state == LinkState.READY
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(tr("LIVE", "DIRECT"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (live.running) {
                    PillButton(tr("Stop", "Arrêter"), { Graph.car.stopLive() }, Modifier.weight(1f), style = PillStyle.Filled)
                } else {
                    PillButton(tr("Start", "Démarrer"), { Graph.car.startLive() }, Modifier.weight(1f), style = PillStyle.Accent, enabled = connected)
                }
                PillButton(tr("Share CSV", "Partager CSV"), { shareCsv(context) }, Modifier.weight(1f), enabled = live.samples > 0)
            }
            if (!connected && !live.running) {
                Text(tr("Connect to the car on the Home tab first.", "Connectez-vous d'abord à la voiture depuis l'Accueil."), style = Type.body, color = P.textDim)
            }
            if (live.running || live.samples > 0) {
                Text(
                    tr("${live.samples} samples · %.1f / s", "${live.samples} mesures · %.1f / s").format(live.rate) +
                        (live.battery?.let { " · %.1f V".format(it) } ?: ""),
                    style = Type.label, color = P.textFaint,
                )
            }

            val issues = findings.filter { it.level == Level.WARN || it.level == Level.WATCH }
            if (issues.isNotEmpty()) {
                Card(onClick = onHealth) {
                    for (f in issues.take(3)) {
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(levelColor(f.level))
                            Spacer(Modifier.width(10.dp))
                            Text(f.title.local, style = Type.title, color = P.text)
                        }
                    }
                    Text(tr("Details in Check-up", "Détails dans Bilan"), style = Type.label, color = P.textDim)
                }
            }

            if (live.values.isEmpty()) {
                Card {
                    SectionLabel(tr("What to do", "Mode d'emploi"))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(
                            "Start with the engine cold if you can, then drive normally: the app records every value the ECU gives and checks battery and charging, warm-up, turbo boost, rail pressure, air flow and EGR as you go. For the turbo check, accelerate fully in 3rd or 4th from ~1,800 to ~3,500 rpm where it is safe. Stop the session at the end of the drive to save its check-up.",
                            "Démarrez moteur froid si possible, puis roulez normalement : l'appli enregistre toutes les valeurs du calculateur et vérifie au fil de l'eau batterie et charge, montée en température, pression turbo, pression rail, débit d'air et EGR. Pour tester le turbo, accélérez à fond en 3e ou 4e de ~1 800 à ~3 500 tr/min là où c'est sans danger. Arrêtez la session en fin de trajet pour enregistrer son bilan.",
                        ),
                        style = Type.body, color = P.textDim,
                    )
                }
            }

            for (group in PidGroup.entries) {
                val signals = live.values.values.filter { live.groups[it.key] == group }
                if (signals.isEmpty()) continue
                SectionLabel(group.title.local)
                for (pair in signals.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (s in pair) Gauge(s, Modifier.weight(1f))
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Gauge(s: Signal, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(P.surface)
            .border(1.dp, P.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            Text(s.name.local.uppercase(), style = Type.label, color = P.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.formatted(), style = Type.gauge, color = P.text, maxLines = 1)
                if (s.unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(s.unit, style = Type.label, color = P.textFaint, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

private fun shareCsv(context: Context) {
    val file = Graph.car.exportCsv() ?: return
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, file.name))
    }.onFailure { Graph.toast(tr("Couldn't share: ${it.message}", "Partage impossible : ${it.message}")) }
}
