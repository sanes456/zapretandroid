package com.zapret.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zapret.android.core.ByeDpiProxy
import com.zapret.android.core.CiadpiArgs
import com.zapret.android.data.Prefs
import com.zapret.android.util.Notifications
import com.zapret.android.util.Signals
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Proxy-only mode: runs ciadpi as a local SOCKS5 server without VPN.
 *
 * Useful together with third-party VPN apps (e.g. AdGuard) that can forward
 * traffic to a SOCKS5 proxy, or with apps that support proxy configuration.
 */
class ProxyModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val starting = AtomicBoolean(false)

    private var proxy: ByeDpiProxy? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEverything()

            ACTION_START -> {
                startForegroundCompat(getString(R.string.notif_starting))
                if (running || proxy != null || !starting.compareAndSet(false, true)) {
                    Log.w(TAG, "start ignored, already running or starting")
                    return START_NOT_STICKY
                }
                scope.launch { startProxy() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    private fun startProxy() {
        try {
            val prefs = Prefs(this)
            val args = CiadpiArgs.build(prefs)
            Log.i(TAG, "ciadpi args: ${args.joinToString(" ")}")

            val p = ByeDpiProxy()
            p.onExit = { rc -> onProxyExit(rc) }
            p.start(args)
            proxy = p

            running = true
            updateNotification(getString(R.string.notif_proxy_running, CiadpiArgs.PROXY_PORT))
            Signals.broadcastStateChanged(this)
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start proxy", t)
            stopEverything()
            Notifications.showError(this, t.message ?: t.javaClass.simpleName)
        } finally {
            starting.set(false)
        }
    }

    private fun onProxyExit(rc: Int) {
        Log.i(TAG, "proxy exited, rc=$rc")
        val wasRunning = running
        running = false
        proxy = null
        if (!wasRunning) return

        stopForegroundAndSelf()
        Notifications.showError(this, getString(R.string.notif_proxy_stopped, rc))
        Signals.broadcastStateChanged(this)
    }

    private fun stopEverything() {
        running = false
        proxy?.stop()
        proxy = null
        stopForegroundAndSelf()
        Signals.broadcastStateChanged(this)
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(text: String) {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_PROXY,
            Notifications.buildStatus(this, text, stopPendingIntent()),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(Notifications.ID_PROXY, Notifications.buildStatus(this, text, stopPendingIntent()))
    }

    private fun stopPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, ProxyModeService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val TAG = "ProxyModeService"

        const val ACTION_START = "com.zapret.android.action.PROXY_START"
        const val ACTION_STOP = "com.zapret.android.action.PROXY_STOP"

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, ProxyModeService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProxyModeService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
