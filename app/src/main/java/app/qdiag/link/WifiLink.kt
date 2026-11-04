package app.qdiag.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.PatternMatcher
import app.qdiag.core.elm.Transport
import app.qdiag.diag.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.resume

/**
 * The TCP link to a Wi-Fi ELM327. The adapter makes its own Wi-Fi with no internet, so the
 * socket must be opened on that network explicitly (otherwise Android routes it over mobile
 * data). Two ways in:
 *  - the phone is already on the adapter's Wi-Fi (joined in Android's settings): use it;
 *  - otherwise ask Android to join it for this app only ([WifiNetworkSpecifier], a system
 *    prompt lists the matching networks). Mobile data then stays the phone's internet, so
 *    updates and diagnostics still get out while connected to the car.
 */
class WifiLink(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var requested: ConnectivityManager.NetworkCallback? = null

    /** Wi-Fi networks the phone is currently on. */
    @Suppress("DEPRECATION")
    private fun wifiNetworks(): List<Network> = cm.allNetworks.filter { n ->
        cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /** Opens a socket to [host]:[port], joining the adapter's Wi-Fi if needed. */
    suspend fun open(host: String, port: Int, ssidPrefix: String, onStatus: (String) -> Unit): Socket {
        for (n in wifiNetworks()) {
            val s = tryConnect(n, host, port)
            if (s != null) {
                Diag.log("link.wifi", "how" to "current")
                return s
            }
        }
        if (ssidPrefix.isBlank()) throw IOException("adapter not reachable on the current Wi-Fi")
        onStatus("request")
        val network = request(ssidPrefix) ?: throw IOException("adapter Wi-Fi not joined")
        Diag.log("link.wifi", "how" to "requested")
        return tryConnect(network, host, port) ?: throw IOException("adapter not answering on $host:$port")
    }

    private suspend fun tryConnect(network: Network, host: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        try {
            network.socketFactory.createSocket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), 3_000)
            }
        } catch (e: IOException) {
            Diag.log("link.try", "error" to e.toString())
            null
        }
    }

    /** Android's "connect to device" prompt for networks named [ssidPrefix]…; null if refused or not found. */
    private suspend fun request(ssidPrefix: String): Network? {
        release()
        val spec = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher(ssidPrefix, PatternMatcher.PATTERN_PREFIX))
            .build()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        return withTimeoutOrNull(90_000) {
            suspendCancellableCoroutine { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(network)
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                requested = cb
                cm.requestNetwork(req, cb)
                cont.invokeOnCancellation { release() }
            }
        }
    }

    /** Lets Android drop the adapter's Wi-Fi if this app had asked for it. */
    fun release() {
        requested?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        requested = null
    }
}

/** ELM327 over a socket: writes the command and a carriage return, reads up to the `>` prompt. */
class SocketTransport(private val socket: Socket) : Transport {
    private val input: InputStream = socket.getInputStream()
    private val output: OutputStream = socket.getOutputStream()
    private val buffer = ByteArray(1024)

    override fun exchange(command: String, timeoutMs: Long): String {
        // Anything left over from a reply that timed out would be read as this one's.
        while (input.available() > 0) input.read(buffer, 0, minOf(buffer.size, input.available()))
        output.write((command + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val out = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        socket.soTimeout = 100
        while (System.currentTimeMillis() < deadline) {
            val n = try {
                input.read(buffer)
            } catch (_: SocketTimeoutException) {
                continue
            }
            if (n < 0) throw IOException("adapter closed the connection")
            out.append(String(buffer, 0, n, Charsets.US_ASCII))
            if (out.contains('>')) return out.toString()
        }
        return out.toString()
    }

    fun close() = runCatching { socket.close() }
}
