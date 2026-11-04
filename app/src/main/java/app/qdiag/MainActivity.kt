package app.qdiag

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qdiag.data.ThemeMode
import app.qdiag.diag.Diag
import app.qdiag.diag.Reports
import app.qdiag.ui.AppRoot
import app.qdiag.ui.theme.DarkPalette
import app.qdiag.ui.theme.LightPalette
import app.qdiag.ui.theme.QDiagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars(isDark(Graph.settings.current.theme, systemDark()))
        super.onCreate(savedInstanceState)
        // The "connected to the car" notification that keeps recording going with the screen off.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleIntent(intent)
        val language = Graph.settings.current.language
        setContent {
            val settings by Graph.settings.state.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme, isSystemInDarkTheme())
            LaunchedEffect(dark) { applySystemBars(dark) }
            // UI text is read in the chosen language (see tr()): start over in the new one.
            LaunchedEffect(settings.language) { if (settings.language != language) recreate() }
            QDiagTheme(dark) {
                AppRoot()
            }
        }
    }

    private fun systemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.PAPER -> false
    }

    /** Status / navigation bar icons that read on the chosen theme, and a matching window behind it. */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable((if (dark) DarkPalette else LightPalette).background.toArgb()))
    }

    override fun onStart() {
        super.onStart()
        Graph.updater.checkIfDue()
    }

    override fun onStop() {
        super.onStop()
        // The phone may now get back to the internet: send what's waiting, keep the rest on disk.
        Diag.persist()
        Graph.scope.launch(Dispatchers.IO) {
            Reports.flush()
            Diag.flush()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_INSTALL_STATUS -> Graph.updater.onInstallStatus(this, intent)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "app.qdiag.INSTALL_STATUS"
    }
}
