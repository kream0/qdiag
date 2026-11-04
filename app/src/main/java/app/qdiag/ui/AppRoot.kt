package app.qdiag.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.qdiag.Graph
import app.qdiag.tr
import app.qdiag.ui.screens.CodesScreen
import app.qdiag.ui.screens.ConsoleScreen
import app.qdiag.ui.screens.HealthScreen
import app.qdiag.ui.screens.HomeScreen
import app.qdiag.ui.screens.LiveScreen
import app.qdiag.ui.screens.SettingsScreen
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type
import app.qdiag.update.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.ui.components.PillButton
import app.qdiag.ui.components.PillStyle

enum class Tab(private val en: String, private val fr: String) {
    HOME("HOME", "ACCUEIL"),
    CODES("CODES", "CODES"),
    LIVE("LIVE", "DIRECT"),
    HEALTH("CHECK-UP", "BILAN"),
    CONSOLE("CONSOLE", "CONSOLE");

    val label: String get() = tr(en, fr)
}

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val update by Graph.updater.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    Box(Modifier.fillMaxSize().background(P.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(onTab = { tab = it }, onSettings = { settingsOpen = true })
                    Tab.CODES -> CodesScreen()
                    Tab.LIVE -> LiveScreen(onHealth = { tab = Tab.HEALTH })
                    Tab.HEALTH -> HealthScreen()
                    Tab.CONSOLE -> ConsoleScreen()
                }
            }
            (update as? UpdateState.Ready)?.let { ready ->
                if (Graph.updater.dismissedVersion != ready.remote.versionCode && activity != null) {
                    UpdateBar(
                        version = ready.remote.versionName,
                        onInstall = { Graph.updater.install(activity) },
                        onLater = { Graph.updater.dismissedVersion = ready.remote.versionCode },
                    )
                }
            }
            BottomNav(tab) { tab = it }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally { it / 3 } + fadeIn(),
            exit = slideOutHorizontally { it / 3 } + fadeOut(),
        ) {
            SettingsScreen(onBack = { settingsOpen = false })
        }

        ToastHost(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp))
    }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && tab != Tab.HOME) { tab = Tab.HOME }
}

@Composable
private fun UpdateBar(version: String, onInstall: () -> Unit, onLater: () -> Unit) {
    var hidden by remember { mutableStateOf(false) }
    if (hidden) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(P.surfaceHigh)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr("Update ready · v$version", "Mise à jour prête · v$version"),
            style = Type.title, color = P.text, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            tr("LATER", "PLUS TARD"), style = Type.labelBold, color = P.textDim,
            modifier = Modifier.clip(CircleShape).clickable { hidden = true; onLater() }.padding(10.dp),
        )
        PillButton(tr("Install", "Installer"), onInstall, style = PillStyle.Accent)
    }
}

@Composable
private fun BottomNav(tab: Tab, onTab: (Tab) -> Unit) {
    val codes by Graph.car.codes.collectAsStateWithLifecycle()
    val live by Graph.car.live.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == tab
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTab(t) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) P.accent else Color.Transparent))
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.label, style = Type.labelBold, color = if (selected) P.text else P.textFaint, maxLines = 1)
                    val badge = when (t) {
                        Tab.CODES -> (codes.storedList.size + codes.pendingList.size).takeIf { it > 0 }?.toString()
                        Tab.LIVE -> if (live.running) "●" else null
                        else -> null
                    }
                    if (badge != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(badge, style = Type.labelBold, color = P.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToastHost(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        Graph.messages.collectLatest {
            last = it
            message = it
            delay(2600)
            message = null
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .clip(CircleShape)
                .background(if (P.isDark) Color(0xFF1E1E1E) else Color(0xFF111111))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(P.accent))
            Spacer(Modifier.width(10.dp))
            Text(last, style = Type.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
