package app.qdiag.data

import android.content.Context
import app.qdiag.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's language; French unless you pick English. */
enum class Language(val label: String) {
    FRENCH("FRANÇAIS"),
    ENGLISH("ENGLISH"),
}

enum class ThemeMode(private val en: String, private val fr: String) {
    SYSTEM("SYSTEM", "SYSTÈME"),
    DARK("DARK", "SOMBRE"),
    PAPER("PAPER", "PAPIER");

    val label: String get() = tr(en, fr)
}

data class AppSettings(
    /** The Wi-Fi adapter's address: most ELM327 Wi-Fi clones listen on 192.168.0.10:35000. */
    val host: String = "192.168.0.10",
    val port: Int = 35000,
    /** Start of the adapter's Wi-Fi name, for Android's "connect to this device" prompt. */
    val ssidPrefix: String = "WiFi_OBD",
    /** The last protocol number the car answered on ("6": CAN 11-bit 500 kbit/s), tried first. */
    val protocol: String = "",
    /** Posts what the app does with the car to the developer's diagnostics topic (no location, VIN masked). */
    val sendDiagnostics: Boolean = true,
    /** Lets the console send commands that change things in the car (writes, resets, tests). */
    val expertConsole: Boolean = false,
    val autoUpdate: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.FRENCH,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("qdiag_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            host = prefs.getString("host", null)?.takeIf { it.isNotBlank() } ?: d.host,
            port = prefs.getInt("port", d.port),
            ssidPrefix = prefs.getString("ssidPrefix", null) ?: d.ssidPrefix,
            protocol = prefs.getString("protocol", null) ?: d.protocol,
            sendDiagnostics = prefs.getBoolean("sendDiagnostics", d.sendDiagnostics),
            expertConsole = prefs.getBoolean("expertConsole", d.expertConsole),
            autoUpdate = prefs.getBoolean("autoUpdate", d.autoUpdate),
            theme = enumOr(prefs.getString("theme", null), d.theme),
            language = enumOr(prefs.getString("language", null), d.language),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("host", s.host)
            .putInt("port", s.port)
            .putString("ssidPrefix", s.ssidPrefix)
            .putString("protocol", s.protocol)
            .putBoolean("sendDiagnostics", s.sendDiagnostics)
            .putBoolean("expertConsole", s.expertConsole)
            .putBoolean("autoUpdate", s.autoUpdate)
            .putString("theme", s.theme.name)
            .putString("language", s.language.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
