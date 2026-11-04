package app.qdiag.car

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import app.qdiag.Graph
import app.qdiag.MainActivity
import app.qdiag.R
import app.qdiag.diag.Diag
import app.qdiag.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the app running while it's linked to the car: without it Android freezes the app as soon
 * as the screen turns off or another app comes to the front, and live recording stops. Shows an
 * ongoing notification, holds a partial wake lock and a Wi-Fi lock so the adapter's Wi-Fi stays up.
 */
class LinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updater: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Link to the car", "Liaison avec la voiture"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Shown while QDiag is connected to the car.", "Affichée tant que QDiag est connectée à la voiture.")
                setShowBadge(false)
            },
        )
        startForeground(ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qdiag:link")
            .apply { setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L) }
        wifiLock = runCatching {
            @Suppress("DEPRECATION")
            getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "qdiag:link")
                .apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        Diag.log("service.start")
        updater = scope.launch {
            var last = ""
            while (isActive) {
                val text = text()
                if (text != last) {
                    last = text
                    nm.notify(ID, build())
                }
                delay(3_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        updater?.cancel()
        scope.cancel()
        runCatching { wake?.release() }
        runCatching { wifiLock?.release() }
        Diag.log("service.stop")
        super.onDestroy()
    }

    private fun text(): String {
        val car = Graph.car
        val live = car.live.value
        return when {
            live.running && car.connected -> tr("Recording live data · ${live.samples} samples", "Enregistrement en direct · ${live.samples} mesures")
            car.link.value.state == LinkState.CONNECTING -> tr("Reconnecting to the adapter…", "Reconnexion à l'adaptateur…")
            car.connected -> tr("Connected to the car", "Connecté à la voiture")
            else -> tr("Waiting for the adapter", "En attente de l'adaptateur")
        }
    }

    private fun build(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_qdiag)
            .setContentTitle("QDiag")
            .setContentText(text())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return b.build()
    }

    companion object {
        private const val CHANNEL = "link"
        private const val ID = 7

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LinkService::class.java)) }
                .onFailure { Diag.error("service.fail", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkService::class.java))
        }
    }
}
