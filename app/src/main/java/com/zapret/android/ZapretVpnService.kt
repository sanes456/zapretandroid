package com.zapret.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Patterns
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zapret.android.core.ByeDpiProxy
import com.zapret.android.core.CiadpiArgs
import com.zapret.android.core.HevTunnel
import com.zapret.android.data.Prefs
import com.zapret.android.util.Notifications
import com.zapret.android.util.Signals
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * VPN mode: captures all IP traffic via a TUN device and forwards it through
 * the local ciadpi SOCKS5 server using hev-socks5-tunnel.
 *
 * The application itself is excluded from the tunnel
 * (addDisallowedApplication), so ciadpi's own outbound connections go
 * directly to the network and never loop back into the tunnel.
 */
class ZapretVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val starting = AtomicBoolean(false)

    private var proxy: ByeDpiProxy? = null
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEverything()

            ACTION_START -> {
                startForegroundCompat(getString(R.string.notif_starting))
                if (running || proxy != null || !starting.compareAndSet(false, true)) {
                    Log.w(TAG, "start ignored, already running or starting")
                    return START_NOT_STICKY
                }
                scope.launch { startTunnel() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked")
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    private fun startTunnel() {
        try {
            val prefs = Prefs(this)
            val args = CiadpiArgs.build(prefs)
            Log.i(TAG, "ciadpi args: ${args.joinToString(" ")}")

            val p = ByeDpiProxy()
            p.onExit = { rc -> onProxyExit(rc) }
            p.start(args)
            proxy = p

            val tun = buildTun(prefs)
                ?: throw IllegalStateException("VpnService.establish() returned null")
            tunInterface = tun

            if (!HevTunnel.start(cacheDir, tun.fd)) {
                throw IllegalStateException("hev-socks5-tunnel failed to start")
            }

            running = true
            updateNotification(getString(R.string.notif_connected, CiadpiArgs.PROXY_PORT))
            Signals.broadcastStateChanged(this)
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start tunnel", t)
            stopEverything()
            Notifications.showError(this, t.message ?: t.javaClass.simpleName)
        } finally {
            starting.set(false)
        }
    }

    private fun buildTun(prefs: Prefs): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(TUN_MTU)
            .addAddress(TUN_ADDRESS_V4, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(validDns(prefs))
            .addDisallowedApplication(packageName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        if (prefs.ipv6) {
            builder.addAddress(TUN_ADDRESS_V6, 128)
            builder.addRoute("::", 0)
        }
        return builder.establish()
    }

    private fun validDns(prefs: Prefs): String {
        val dns = prefs.dns.trim()
        return if (dns.isNotEmpty() && Patterns.IP_ADDRESS.matcher(dns).matches()) {
            dns
        } else {
            Prefs.DEFAULT_DNS
        }
    }

    private fun onProxyExit(rc: Int) {
        Log.i(TAG, "proxy exited, rc=$rc")
        val wasRunning = running
        running = false
        proxy = null
        if (!wasRunning) return

        HevTunnel.stop()
        closeTun()
        stopForegroundAndSelf()
        Notifications.showError(this, getString(R.string.notif_proxy_stopped, rc))
        Signals.broadcastStateChanged(this)
    }

    private fun stopEverything() {
        running = false
        HevTunnel.stop()
        closeTun()
        proxy?.stop()
        proxy = null
        stopForegroundAndSelf()
        Signals.broadcastStateChanged(this)
    }

    private fun closeTun() {
        try {
            tunInterface?.close()
        } catch (e: IOException) {
            Log.w(TAG, "failed to close tun fd", e)
        }
        tunInterface = null
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(text: String) {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_VPN,
            Notifications.buildStatus(this, text, stopPendingIntent()),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(Notifications.ID_VPN, Notifications.buildStatus(this, text, stopPendingIntent()))
    }

    private fun stopPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, ZapretVpnService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val TAG = "ZapretVpnService"

        const val ACTION_START = "com.zapret.android.action.VPN_START"
        const val ACTION_STOP = "com.zapret.android.action.VPN_STOP"

        private const val TUN_ADDRESS_V4 = "10.111.222.1"
        private const val TUN_ADDRESS_V6 = "fd00:2:fd00:1:fd00:1:fd00:1"
        private const val TUN_MTU = 1500

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, ZapretVpnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZapretVpnService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
