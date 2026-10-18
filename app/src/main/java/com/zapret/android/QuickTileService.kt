package com.zapret.android

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zapret.android.data.Prefs

class QuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(currentlyRunning())
    }

    override fun onClick() {
        super.onClick()
        val running = currentlyRunning()
        if (running) {
            stop()
        } else {
            start()
        }
        updateTileState(!running)
    }

    private fun start() {
        val prefs = Prefs(applicationContext)
        if (prefs.mode == Prefs.MODE_PROXY) {
            ProxyModeService.start(applicationContext)
            return
        }
        if (VpnService.prepare(applicationContext) != null) {
            openApp()
        } else {
            ZapretVpnService.start(applicationContext)
        }
    }

    private fun stop() {
        val prefs = Prefs(applicationContext)
        if (prefs.mode == Prefs.MODE_PROXY) {
            ProxyModeService.stop(applicationContext)
        } else {
            ZapretVpnService.stop(applicationContext)
        }
    }

    private fun currentlyRunning(): Boolean {
        val prefs = Prefs(applicationContext)
        return if (prefs.mode == Prefs.MODE_PROXY) {
            ProxyModeService.running
        } else {
            ZapretVpnService.running
        }
    }

    private fun openApp() {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (running) R.string.status_connected else R.string.status_disconnected
            )
        }
        tile.updateTile()
    }
}
