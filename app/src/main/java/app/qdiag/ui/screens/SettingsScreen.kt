package app.qdiag.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.BuildConfig
import app.qdiag.Graph
import app.qdiag.data.AppSettings
import app.qdiag.data.Language
import app.qdiag.data.ThemeMode
import app.qdiag.diag.Diag
import app.qdiag.diag.Reports
import app.qdiag.tr
import app.qdiag.ui.components.Card
import app.qdiag.ui.components.Field
import app.qdiag.ui.components.NothingSwitch
import app.qdiag.ui.components.PillButton
import app.qdiag.ui.components.PillStyle
import app.qdiag.ui.components.Segmented
import app.qdiag.ui.components.SectionLabel
import app.qdiag.ui.components.SettingRow
import app.qdiag.ui.components.openUrl
import app.qdiag.ui.components.timeAgo
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type
import app.qdiag.update.UpdateState
import android.app.Activity

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val update by Graph.updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val d = AppSettings()
    var host by remember { mutableStateOf(settings.host) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    var ssid by remember { mutableStateOf(settings.ssidPrefix) }

    fun saveAdapter() {
        Graph.settings.update {
            it.copy(
                host = host.trim().ifEmpty { d.host },
                port = port.trim().toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: d.port,
                ssidPrefix = ssid.trim(),
            )
        }
        Graph.toast(tr("Saved", "Enregistré"))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(P.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)) {
            Text(
                "←", style = Type.titleLarge, color = P.text,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 12.dp),
            )
            Text(tr("SETTINGS", "RÉGLAGES"), style = Type.display, color = P.text)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                SectionLabel(tr("Adapter", "Adaptateur"))
                Spacer(Modifier.height(10.dp))
                Text(tr("ADDRESS AND PORT", "ADRESSE ET PORT"), style = Type.label, color = P.textDim)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(host, { host = it }, Modifier.weight(2f), placeholder = d.host, keyboard = KeyboardType.Uri)
                    Field(port, { port = it.filter(Char::isDigit) }, Modifier.weight(1f), placeholder = d.port.toString(), keyboard = KeyboardType.Number)
                }
                Spacer(Modifier.height(10.dp))
                Text(tr("WI-FI NAME STARTS WITH", "NOM DU WI-FI COMMENÇANT PAR"), style = Type.label, color = P.textDim)
                Spacer(Modifier.height(6.dp))
                Field(ssid, { ssid = it }, Modifier.fillMaxWidth(), placeholder = d.ssidPrefix)
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "Most Wi-Fi ELM327 adapters use 192.168.0.10, port 35000, and a Wi-Fi named WiFi_OBDII. Leave the name empty to only use the Wi-Fi the phone is already on.",
                        "La plupart des adaptateurs ELM327 Wi-Fi utilisent 192.168.0.10, port 35000, et un Wi-Fi nommé WiFi_OBDII. Laissez le nom vide pour n'utiliser que le Wi-Fi déjà connecté.",
                    ),
                    style = Type.body, color = P.textFaint,
                )
                if (settings.protocol.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SettingRow(
                        tr("Protocol ${settings.protocol} remembered", "Protocole ${settings.protocol} mémorisé"),
                        tr("Tried first next time.", "Essayé en premier la prochaine fois."),
                    ) {
                        PillButton(tr("Forget", "Oublier"), { Graph.settings.update { it.copy(protocol = "") } })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr("Defaults", "Par défaut"), {
                        host = d.host; port = d.port.toString(); ssid = d.ssidPrefix
                    }, Modifier.weight(1f))
                    PillButton(tr("Save", "Enregistrer"), { saveAdapter() }, Modifier.weight(1f), style = PillStyle.Filled)
                }
            }

            Card {
                SectionLabel(tr("Display", "Affichage"))
                Spacer(Modifier.height(10.dp))
                Text(tr("LANGUAGE", "LANGUE"), style = Type.label, color = P.textDim)
                Spacer(Modifier.height(6.dp))
                Segmented(Language.entries.map { it.label }, settings.language.ordinal, { i ->
                    Graph.settings.update { it.copy(language = Language.entries[i]) }
                })
                Spacer(Modifier.height(12.dp))
                Text(tr("THEME", "THÈME"), style = Type.label, color = P.textDim)
                Spacer(Modifier.height(6.dp))
                Segmented(ThemeMode.entries.map { it.label }, settings.theme.ordinal, { i ->
                    Graph.settings.update { it.copy(theme = ThemeMode.entries[i]) }
                })
            }

            Card {
                SectionLabel(tr("Logbook", "Carnet"))
                SettingRow(
                    tr("Send readings to the car logbook", "Envoyer les relevés au carnet"),
                    tr(
                        "Each code reading, drive check-up and survey is sent to a channel only your car's VIN unlocks; Claude reads it to update the Carnet Qashqai+2 and its to-do list. ${Reports.pending} waiting to be sent.",
                        "Chaque lecture de codes, bilan de trajet et relevé part sur un canal que seul le VIN de votre voiture permet de retrouver ; Claude le lit pour mettre à jour le Carnet Qashqai+2 et sa liste de choses à faire. ${Reports.pending} en attente d'envoi.",
                    ),
                ) {
                    NothingSwitch(settings.sendReports, { on -> Graph.settings.update { it.copy(sendReports = on) } })
                }
            }

            Card {
                SectionLabel(tr("Diagnostics", "Diagnostics"))
                SettingRow(
                    tr("Send diagnostics to the developer", "Envoyer les diagnostics au développeur"),
                    tr(
                        "What the app exchanges with the car, codes, a summary of live values and errors. No location; the VIN's serial part is masked. Session ${Diag.session}.",
                        "Ce que l'appli échange avec la voiture, les codes, un résumé des valeurs et les erreurs. Pas de position ; la partie série du VIN est masquée. Session ${Diag.session}.",
                    ),
                ) {
                    NothingSwitch(settings.sendDiagnostics, { on -> Graph.settings.update { it.copy(sendDiagnostics = on) } })
                }
                SettingRow(
                    tr("Expert console", "Console experte"),
                    tr(
                        "Lets the console send requests that change things in the car (resets, writes, actuator tests). Off unless you know exactly what a command does.",
                        "Autorise la console à envoyer des requêtes qui modifient la voiture (réinitialisations, écritures, tests d'actionneurs). À laisser désactivé sauf si vous savez exactement ce que fait une commande.",
                    ),
                ) {
                    NothingSwitch(settings.expertConsole, { on -> Graph.settings.update { it.copy(expertConsole = on) } })
                }
            }

            Card {
                SectionLabel(tr("Updates", "Mises à jour"))
                SettingRow(
                    tr("Version", "Version"),
                    Graph.updater.currentVersion + (Graph.updater.lastChecked.takeIf { it > 0 }?.let { tr(" · checked ", " · vérifié ") + timeAgo(it) } ?: ""),
                ) {}
                SettingRow(
                    tr("Download updates automatically", "Télécharger les mises à jour automatiquement"),
                    tr("You still confirm each install.", "Chaque installation reste à confirmer."),
                ) {
                    NothingSwitch(settings.autoUpdate, { on -> Graph.settings.update { it.copy(autoUpdate = on) } })
                }
                val u = update
                Text(
                    when (u) {
                        UpdateState.Idle -> ""
                        UpdateState.Checking -> tr("Checking…", "Vérification…")
                        UpdateState.UpToDate -> tr("Up to date", "À jour")
                        is UpdateState.Available -> tr("v${u.remote.versionName} available", "v${u.remote.versionName} disponible")
                        is UpdateState.Downloading -> tr("Downloading v${u.remote.versionName}… %d %%", "Téléchargement v${u.remote.versionName}… %d %%").format((u.progress * 100).toInt())
                        is UpdateState.Ready -> tr("v${u.remote.versionName} ready to install", "v${u.remote.versionName} prête à installer")
                        is UpdateState.Failed -> u.message
                    },
                    style = Type.body, color = if (u is UpdateState.Failed) P.warn else P.textDim,
                )
                Spacer(Modifier.height(8.dp))
                when (u) {
                    is UpdateState.Available -> PillButton(tr("Download", "Télécharger"), { Graph.updater.startDownload() }, Modifier.fillMaxWidth(), style = PillStyle.Accent)
                    is UpdateState.Ready -> PillButton(tr("Install", "Installer"), { (context as? Activity)?.let { Graph.updater.install(it) } }, Modifier.fillMaxWidth(), style = PillStyle.Accent)
                    else -> PillButton(tr("Check now", "Vérifier maintenant"), { Graph.updater.check(manual = true) }, Modifier.fillMaxWidth(), enabled = u !is UpdateState.Checking && u !is UpdateState.Downloading)
                }
            }

            Card {
                SectionLabel("QDiag")
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(
                        "Diagnostics for the Nissan Qashqai+2 2.0 dCi through a Wi-Fi ELM327 adapter. Reads only, except clearing codes when you ask.",
                        "Diagnostic du Nissan Qashqai+2 2.0 dCi via un adaptateur ELM327 Wi-Fi. Lecture seule, sauf l'effacement des codes quand vous le demandez.",
                    ),
                    style = Type.body, color = P.textDim,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "github.com/${BuildConfig.UPDATE_REPO}", style = Type.labelBold, color = P.accent,
                    modifier = Modifier.clickable { openUrl(context, "https://github.com/${BuildConfig.UPDATE_REPO}/releases") }.padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
