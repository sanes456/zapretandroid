package com.zapret.android.core

import android.util.Log
import hev.htproxy.TProxyService
import java.io.File
import java.io.IOException

/**
 * Thin wrapper around hev-socks5-tunnel (bundled prebuilt AAR).
 *
 * The tunnel consumes the VpnService TUN file descriptor and forwards all IP
 * traffic to the local ciadpi SOCKS5 server.
 */
object HevTunnel {

    private const val TAG = "HevTunnel"
    private const val CONFIG_NAME = "hev-socks5-tunnel.yaml"
    private const val TUN_MTU = 1500

    fun start(configDir: File, tunFd: Int): Boolean {
        val conf = File(configDir, CONFIG_NAME)
        try {
            conf.writeText(buildConfig())
        } catch (e: IOException) {
            Log.e(TAG, "failed to write config", e)
            return false
        }

        return try {
            TProxyService.TProxyStartService(conf.absolutePath, tunFd)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "TProxyStartService failed", t)
            false
        }
    }

    fun stop() {
        try {
            TProxyService.TProxyStopService()
        } catch (t: Throwable) {
            Log.e(TAG, "TProxyStopService failed", t)
        }
    }

    private fun buildConfig(): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: $TUN_MTU")
        appendLine("socks5:")
        appendLine("  address: ${CiadpiArgs.PROXY_HOST}")
        appendLine("  port: ${CiadpiArgs.PROXY_PORT}")
        appendLine("  udp: udp")
        appendLine("misc:")
        appendLine("  task-stack-size: 81920")
        appendLine("  log-level: warn")
    }
}
